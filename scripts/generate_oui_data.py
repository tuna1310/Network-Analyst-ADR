#!/usr/bin/env python3
"""
Script phân tích tệp oui.csv chuẩn của IEEE (chứa MA-L, MA-M, MA-S) và sinh ra tệp
resource app/src/main/res/raw/oui_database.txt phục vụ tra cứu MAC vendor trong app.

Cách dùng:
1. Đặt tệp oui.csv (tải từ https://standards-oui.ieee.org/oui/oui.csv) vào thư mục scripts/data/oui.csv
2. Chạy lệnh: python scripts/generate_oui_data.py
"""

import csv
import os
import sys

def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    
    # Tìm tệp oui.csv trong scripts/data/ hoặc Scripts/data/
    possible_csv_paths = [
        os.path.join(project_root, "scripts", "data", "oui.csv"),
        os.path.join(project_root, "Scripts", "data", "oui.csv"),
        os.path.join(script_dir, "data", "oui.csv"),
    ]
    
    csv_path = None
    for p in possible_csv_paths:
        if os.path.exists(p):
            csv_path = p
            break

    output_dir = os.path.join(project_root, "app", "src", "main", "res", "raw")
    os.makedirs(output_dir, exist_ok=True)
    output_path = os.path.join(output_dir, "oui_database.txt")

    if not csv_path:
        print(f"[!] Không tìm thấy tệp oui.csv tại các đường dẫn: {possible_csv_paths}")
        print("[*] Hãy tải oui.csv từ https://standards-oui.ieee.org/oui/oui.csv và đặt vào scripts/data/oui.csv")
        sys.exit(1)

    print(f"[*] Đang đọc và phân tích {csv_path}...")
    
    oui_map = {}
    total_rows = 0

    with open(csv_path, mode="r", encoding="utf-8", errors="replace") as f:
        reader = csv.reader(f)
        header = next(reader, None)
        
        # Cấu trúc IEEE oui.csv: Registry,Assignment,Organization Name,Organization Address
        # Index: 0: Registry, 1: Assignment, 2: Organization Name
        assignment_idx = 1
        org_idx = 2
        
        if header:
            for i, col in enumerate(header):
                col_clean = col.strip().lower()
                if "assignment" in col_clean:
                    assignment_idx = i
                elif "organization" in col_clean and "name" in col_clean:
                    org_idx = i

        for row in reader:
            total_rows += 1
            if len(row) <= max(assignment_idx, org_idx):
                continue
            
            raw_assignment = row[assignment_idx].strip().replace(":", "").replace("-", "").replace(".", "").upper()
            raw_org = row[org_idx].strip()
            
            # Xử lý dọn dẹp tên tổ chức
            org_clean = raw_org.replace("\n", " ").replace("\r", " ").replace("=", " ").strip()
            # Rút gọn khoảng trắng thừa
            org_clean = " ".join(org_clean.split())
            
            if raw_assignment and org_clean:
                oui_map[raw_assignment] = org_clean

    print(f"[*] Đã phân tích {total_rows} dòng CSV. Tổng số prefix hợp lệ: {len(oui_map)}")
    print(f"[*] Đang ghi kết quả vào {output_path}...")

    # Sắp xếp theo thứ tự prefix để dễ tìm kiếm và nén
    sorted_items = sorted(oui_map.items(), key=lambda x: (len(x[0]), x[0]))

    with open(output_path, mode="w", encoding="utf-8") as out:
        out.write("# IEEE OUI Database (MA-L 24-bit, MA-M 28-bit, MA-S 36-bit)\n")
        for prefix, vendor in sorted_items:
            out.write(f"{prefix}={vendor}\n")

    print(f"[✓] Hoàn tất! Đã xuất {len(sorted_items)} mục vào {output_path}")

if __name__ == "__main__":
    main()
