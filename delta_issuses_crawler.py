import requests
import os
import re
import time
from pathlib import Path

# Cấu hình
REPO_OWNER = "delta-io"
REPO_NAME = "delta"
OUTPUT_DIR = "delta_issues"
PER_PAGE = 100

def sanitize_filename(title):
    """Chuẩn hóa tên file để đảm bảo hợp lệ"""
    if not title or not isinstance(title, str):
        title = "no_title"
    title = re.sub(r'[\\/*?:"<>|]', "", title)
    title = title.strip()
    title = re.sub(r'\s+', ' ', title)
    return title[:100]

def save_issue_to_file(issue, index):
    """Lưu từng issue thành file với xử lý lỗi đầy đủ"""
    try:
        # Xử lý title
        title = issue.get("title") if issue.get("title") else f"no_title_{index}"
        
        # Xử lý body - quan trọng nhất
        body = issue.get("body")
        if body is None:
            body = "No content available"
        elif not isinstance(body, str):
            body = str(body)
        
        # Tạo tên file an toàn
        safe_title = sanitize_filename(title)
        filename = f"{index:04d}_{safe_title}.txt"
        
        # Tạo thư mục nếu chưa tồn tại
        os.makedirs(OUTPUT_DIR, exist_ok=True)
        
        # Lưu file
        filepath = os.path.join(OUTPUT_DIR, filename)
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(body)
        
        return filename
    except Exception as e:
        print(f"Lỗi khi lưu issue {index}: {e}")
        return None

def get_all_issues():
    """Lấy tất cả issues với xử lý lỗi đầy đủ"""
    issues = []
    page = 1
    
    while True:
        print(f"Đang lấy trang {page}...")
        
        url = f"https://api.github.com/repos/{REPO_OWNER}/{REPO_NAME}/issues"
        params = {
            "state": "all",
            "per_page": PER_PAGE,
            "page": page
        }
        
        try:
            # Gửi request với timeout
            response = requests.get(url, params=params, timeout=10)
            
            # Debug: In status code và rate limit
            print(f"Status: {response.status_code} | RateLimit: {response.headers.get('X-RateLimit-Remaining')}/{response.headers.get('X-RateLimit-Limit')}")
            
            # Xử lý rate limit
            if response.status_code == 403:
                reset_time = int(response.headers.get('X-RateLimit-Reset', time.time() + 3600))
                sleep_time = max(reset_time - time.time(), 0) + 15
                print(f"Bị rate limit, chờ {sleep_time:.1f} giây...")
                time.sleep(sleep_time)
                continue
                
            # Xử lý lỗi HTTP
            if response.status_code != 200:
                print(f"Lỗi API: {response.status_code} - {response.text[:200]}")
                break
                
            # Parse JSON
            try:
                new_issues = response.json()
            except ValueError as e:
                print(f"Lỗi parse JSON: {e}")
                break
                
            if not new_issues:
                print("Không còn issues nào!")
                break
                
            # Lưu từng issue
            success_count = 0
            for i, issue in enumerate(new_issues, start=len(issues)+1):
                if save_issue_to_file(issue, i):
                    success_count += 1
            
            print(f" → Đã lưu {success_count}/{len(new_issues)} issues trang {page}")
            issues.extend(new_issues)
            
            # Kiểm tra trang cuối
            if len(new_issues) < PER_PAGE:
                break
                
            page += 1
            time.sleep(1)  # Tránh rate limit
            
        except requests.exceptions.RequestException as e:
            print(f"Lỗi kết nối: {e}")
            break
        except Exception as e:
            print(f"Lỗi không xác định: {e}")
            break
    
    return issues

if __name__ == "__main__":
    print(f"Bắt đầu crawl issues từ {REPO_OWNER}/{REPO_NAME}...")
    print(f"Dữ liệu sẽ được lưu vào thư mục: {os.path.abspath(OUTPUT_DIR)}")
    
    issues = get_all_issues()
    
    print("\nKết quả cuối cùng:")
    print(f"- Tổng issues nhận được: {len(issues)}")
    print(f"- Số file đã lưu: {len(os.listdir(OUTPUT_DIR)) if os.path.exists(OUTPUT_DIR) else 0}")
    print(f"- Đường dẫn thư mục: {os.path.abspath(OUTPUT_DIR)}")