package com.networkanalyst.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

class LocalizationConsistencyTest {

    private fun findProjectDirectory(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Không tìm thấy đường dẫn: $relativePath trong $candidates")
    }

    private fun extractStringKeys(file: File): Set<String> {
        val content = file.readText()
        val pattern = Pattern.compile("""<string\s+name="([^"]+)"""")
        val matcher = pattern.matcher(content)
        val keys = mutableSetOf<String>()
        while (matcher.find()) {
            matcher.group(1)?.let { keys.add(it) }
        }
        return keys
    }

    @Test
    fun testStringsKeys_areIdenticalBetweenEnglishAndVietnamese() {
        val enFile = findProjectDirectory("src/main/res/values/strings.xml")
        val viFile = findProjectDirectory("src/main/res/values-vi/strings.xml")

        assertTrue("File English strings.xml không tồn tại!", enFile.exists())
        assertTrue("File Vietnamese strings.xml không tồn tại!", viFile.exists())

        val enKeys = extractStringKeys(enFile)
        val viKeys = extractStringKeys(viFile)

        val missingInVi = enKeys - viKeys
        val missingInEn = viKeys - enKeys

        assertTrue(
            "Các key có trong values/strings.xml nhưng thiếu trong values-vi/strings.xml: $missingInVi",
            missingInVi.isEmpty()
        )

        assertTrue(
            "Các key có trong values-vi/strings.xml nhưng thiếu trong values/strings.xml: $missingInEn",
            missingInEn.isEmpty()
        )

        assertEquals("Số lượng string key trong 2 file phải bằng nhau", enKeys.size, viKeys.size)
        assertTrue("Phải có ít nhất 150 chuỗi bản địa hóa", enKeys.size >= 150)
    }

    @Test
    fun testNoHardcodedVietnameseCharactersInSourceCode() {
        val srcDir = findProjectDirectory("src/main/java")
        val vietnameseRegex = Pattern.compile(
            "[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđĐ]",
            Pattern.CASE_INSENSITIVE
        )

        val violations = mutableListOf<String>()
        var inspectedFiles = 0

        srcDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            inspectedFiles++
            val lines = file.readLines()
            var inBlockComment = false

            lines.forEachIndexed { index, line ->
                val trimmed = line.trim()

                // Bỏ qua chú thích nhiều dòng
                if (inBlockComment) {
                    if (trimmed.contains("*/")) {
                        inBlockComment = false
                    }
                    return@forEachIndexed
                }
                if (trimmed.startsWith("/*")) {
                    if (!trimmed.contains("*/")) {
                        inBlockComment = true
                    }
                    return@forEachIndexed
                }

                // Bỏ qua chú thích một dòng
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) {
                    return@forEachIndexed
                }

                // Bỏ qua phần chú thích inline // ở cuối dòng
                val codeOnly = if (line.contains("//")) {
                    line.substringBefore("//")
                } else {
                    line
                }

                // Loại trừ các lệnh ghi Log kỹ thuật nội bộ nếu có
                val isLogLine = codeOnly.trim().startsWith("Log.") || codeOnly.trim().startsWith("println(")
                if (isLogLine) {
                    return@forEachIndexed
                }

                if (vietnameseRegex.matcher(codeOnly).find()) {
                    violations.add("${file.name}:${index + 1} -> $trimmed")
                }
            }
        }

        assertTrue(
            "Đã kiểm tra $inspectedFiles tệp .kt nhưng phát hiện ${violations.size} chuỗi tiếng Việt viết thẳng:\n" +
                    violations.joinToString("\n"),
            violations.isEmpty()
        )
        assertTrue("Số tệp Kotlin được quét phải lớn hơn 20", inspectedFiles >= 20)
    }

    @Test
    fun testNoHardcodedMultiWordLiteralsInUiProperties() {
        val srcDir = findProjectDirectory("src/main/java")
        // Bắt Text("..."), errorMessage = "...", contentDescription = "..."
        val uiPropertyPattern = Pattern.compile(
            """(?:Text\s*\(\s*(?:text\s*=\s*)?"([^"]+)"|errorMessage\s*=\s*"([^"]+)"|contentDescription\s*=\s*"([^"]+)")"""
        )

        // Danh sách loại trừ hợp lệ (dữ liệu mẫu định danh mạng/kỹ thuật)
        val EXCLUSION_LIST = setOf(
            "google.com",
            "1.1.1.1",
            "AS15169"
        )

        val violations = mutableListOf<String>()
        var inspectedFiles = 0

        srcDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            inspectedFiles++
            val lines = file.readLines()
            var inBlockComment = false

            lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (inBlockComment) {
                    if (trimmed.contains("*/")) inBlockComment = false
                    return@forEachIndexed
                }
                if (trimmed.startsWith("/*")) {
                    if (!trimmed.contains("*/")) inBlockComment = true
                    return@forEachIndexed
                }
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed

                val codeOnly = if (line.contains("//")) line.substringBefore("//") else line
                if (codeOnly.trim().startsWith("Log.") || codeOnly.trim().startsWith("println(")) {
                    return@forEachIndexed
                }

                val matcher = uiPropertyPattern.matcher(codeOnly)
                while (matcher.find()) {
                    val literal = matcher.group(1) ?: matcher.group(2) ?: matcher.group(3) ?: ""
                    // Đếm số từ có chứa chữ cái (phân tách bởi khoảng trắng)
                    val words = literal.trim().split(Regex("""\s+""")).filter { word ->
                        word.any { it.isLetter() }
                    }
                    if (words.size >= 2 && literal !in EXCLUSION_LIST) {
                        violations.add("${file.name}:${index + 1} [literal: \"$literal\"] -> $trimmed")
                    }
                }
            }
        }

        assertTrue(
            "Phát hiện ${violations.size} chuỗi UI nhiều từ viết thẳng (phải dùng stringResource/UiText):\n" +
                    violations.joinToString("\n"),
            violations.isEmpty()
        )
        assertTrue("Số tệp Kotlin được quét phải lớn hơn 20", inspectedFiles >= 20)
    }
}
