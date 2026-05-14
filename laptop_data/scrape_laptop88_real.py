import requests
from bs4 import BeautifulSoup
import json
import time

def get_product_urls(category_url, max_urls=100):
    urls = []
    page = 1
    headers = {'User-Agent': 'Mozilla/5.0'}
    
    while len(urls) < max_urls:
        url = f"{category_url}?page={page}"
        print(f"Fetching category page {page}...")
        try:
            res = requests.get(url, headers=headers, timeout=10)
            res.raise_for_status()
        except Exception as e:
            print(f"Error fetching page {page}: {e}")
            break
            
        soup = BeautifulSoup(res.text, 'html.parser')
        items = soup.find_all('div', class_='pro-item')
        if not items:
            items = soup.find_all('div', class_='product-item')
        
        if not items:
            print("No more items found.")
            break
            
        for item in items:
            link_tag = item.find('a')
            if link_tag and 'href' in link_tag.attrs:
                href = link_tag['href']
                if not href.startswith('http'):
                    href = 'https://laptop88.vn' + href
                if href not in urls:
                    urls.append(href)
                    if len(urls) >= max_urls:
                        break
        page += 1
        time.sleep(1)
    return urls[:max_urls]

def scrape_product(url):
    headers = {'User-Agent': 'Mozilla/5.0'}
    print(f"Scraping product: {url}")
    try:
        res = requests.get(url, headers=headers, timeout=10)
        res.raise_for_status()
    except Exception as e:
        print(f"Error scraping {url}: {e}")
        return None
        
    soup = BeautifulSoup(res.text, 'html.parser')
    
    name_tag = soup.find('div', class_='product-name') or soup.find('h1')
    name = name_tag.text.strip() if name_tag else "Unknown Laptop"
    
    desc_html = ""
    desc_div = soup.find('div', class_='content-desc') or soup.find('div', class_='content-desc-detail')
    if desc_div:
        desc_html = str(desc_div)
        desc_html = desc_html.replace('src="/media/', 'src="https://laptop88.vn/media/')
        desc_html = desc_html.replace('src="/upload/', 'src="https://laptop88.vn/upload/')
        desc_html = desc_html.replace('https://zalo.me/4003397725440619448', 'https://zalo.me/0344284548')
        
    specs = {}
    tables = soup.find_all('table')
    for t in tables:
        rows = t.find_all('tr')
        if len(rows) > 5:
            for row in rows:
                cols = row.find_all(['td', 'th'])
                if len(cols) == 2:
                    key = cols[0].text.strip().replace(':', '')
                    val = cols[1].text.strip()
                    specs[key] = val
            break # Usually the main specs table is the first big one
            
    # Normalize specs keys
    normalized_specs = {}
    mapping = {
        'CPU': 'CPU', 'Vi xử lý': 'CPU', 'Bộ vi xử lý': 'CPU', 'Processor': 'CPU',
        'RAM': 'RAM', 'Bộ nhớ trong': 'RAM', 'Memory': 'RAM',
        'Ổ cứng': 'Ổ cứng', 'Lưu trữ': 'Ổ cứng', 'Storage': 'Ổ cứng',
        'VGA': 'Card đồ họa', 'Card đồ họa': 'Card đồ họa', 'Card màn hình': 'Card đồ họa', 'Graphics': 'Card đồ họa',
        'Màn hình': 'Màn hình', 'Hiển thị': 'Màn hình', 'Display': 'Màn hình',
        'Pin': 'Pin & Nguồn', 'Thông số pin': 'Pin & Nguồn', 'Battery': 'Pin & Nguồn',
        'Trọng lượng': 'Trọng lượng', 'Cân nặng': 'Trọng lượng', 'Weight': 'Trọng lượng',
        'Kích thước': 'Kích thước', 'Dimensions': 'Kích thước',
        'Hệ điều hành': 'Hệ điều hành', 'Operating System': 'Hệ điều hành',
        'Màu sắc': 'Màu sắc', 'Case Color': 'Màu sắc',
        'Cổng giao tiếp': 'Cổng kết nối', 'Cổng kết nối': 'Cổng kết nối', 'Standard Ports': 'Cổng kết nối',
        'Chất liệu': 'Chất liệu', 'Case Material': 'Chất liệu',
        'Webcam': 'Webcam', 'Camera': 'Webcam',
        'Âm thanh': 'Âm thanh', 'Audio': 'Âm thanh',
        'Bàn phím': 'Bàn phím & Touchpad', 'Keyboard': 'Bàn phím & Touchpad',
        'Mạng không dây': 'Mạng & Không dây', 'WLAN + Bluetooth': 'Mạng & Không dây'
    }
    
    for k, v in specs.items():
        matched = False
        for mk, mv in mapping.items():
            if mk in k or k in mk:
                normalized_specs[mv] = v
                matched = True
                break
        if not matched:
            normalized_specs[k] = v
            
    # Fill missing required keys with default or empty
    for k in ["CPU", "RAM", "Ổ cứng", "Card đồ họa", "Màn hình", "Pin & Nguồn", "Trọng lượng", "Kích thước", "Hệ điều hành", "Màu sắc", "Cổng kết nối", "Chất liệu", "Webcam", "Âm thanh", "Bàn phím & Touchpad", "Mạng & Không dây"]:
        if k not in normalized_specs:
            normalized_specs[k] = "-"
            
    images = []
    img_divs = soup.find_all('div', class_='img-item')
    for div in img_divs:
        img = div.find('img')
        if img and 'src' in img.attrs:
            src = img['src']
            if not src.startswith('http'):
                src = 'https://laptop88.vn' + src
            images.append(src)
            
    if not images:
        main_img = soup.find('div', class_='product-image')
        if main_img:
            img = main_img.find('img')
            if img and 'src' in img.attrs:
                src = img['src']
                if not src.startswith('http'):
                    src = 'https://laptop88.vn' + src
                images.append(src)

    # Khai thác thêm ảnh từ phần mô tả để đảm bảo có nhiều ảnh
    if desc_html:
        desc_soup = BeautifulSoup(desc_html, 'html.parser')
        desc_imgs = desc_soup.find_all('img')
        for img in desc_imgs:
            if 'src' in img.attrs:
                src = img['src']
                if 'ntzalo' in src or 'linhngay' in src: # Bỏ qua icon zalo/messenger
                    continue
                if src not in images:
                    images.append(src)

    import re
    price = 15000000 # Default fallback
    text = soup.get_text()
    price_matches = re.findall(r'(\d{1,3}(\.\d{3})+)\s*(?:VNĐ|đ|Đ)', text)
    if price_matches:
        for match in price_matches:
            try:
                price_str = match[0].replace('.', '')
                p = int(price_str)
                if p > 1000000: # Bỏ qua các mức giá nhỏ dưới 1 triệu (thường là quà tặng)
                    price = p
                    break
            except:
                pass
            
    # Try to determine brand from name
    brand = "Other"
    brands = ["Dell", "HP", "Asus", "Lenovo", "Acer", "MSI", "Apple", "Macbook"]
    for b in brands:
        if b.lower() in name.lower():
            brand = b
            if b == "Macbook":
                brand = "Apple"
            break
            
    category = "Học tập, Văn phòng"
    full_text = name.lower() + " " + desc_html.lower()
    if 'gaming' in full_text or 'rtx' in full_text or 'loq' in full_text or 'tuf' in full_text or 'legion' in full_text:
        category = 'Gaming'
    elif 'đồ họa' in full_text or 'kỹ thuật' in full_text or 'workstation' in full_text:
        category = 'Đồ họa, Kỹ thuật'
    elif 'mỏng nhẹ' in full_text or 'cao cấp' in full_text or 'zenbook' in full_text or 'xps' in full_text or 'gram' in full_text:
        category = 'Mỏng nhẹ, Cao cấp'

    return {
        "name": name,
        "brand": brand,
        "category": category,
        "description": desc_html,
        "variants": [
            {
                "sku": f"SKU-{int(time.time()*1000)}",
                "price": price,
                "specs": normalized_specs,
                "image_urls": images
            }
        ]
    }

def main():
    urls = get_product_urls('https://laptop88.vn/may-tinh-xach-tay.html', max_urls=100)
    print(f"Found {len(urls)} product URLs")
    
    products = []
    for url in urls:
        p = scrape_product(url)
        if p:
            products.append(p)
        time.sleep(1)
        
    with open('final_gallery_data.json', 'w', encoding='utf-8') as f:
        json.dump(products, f, ensure_ascii=False, indent=2)
    print(f"Saved {len(products)} products to final_gallery_data.json")

if __name__ == '__main__':
    main()
