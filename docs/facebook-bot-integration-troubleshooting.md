# Hướng Dẫn Khắc Phục Lỗi Tích Hợp Facebook Bot

## Vấn Đề Thường Gặp

Khi tích hợp Facebook Bot cho shop, bạn có thể gặp các lỗi sau:

### 1. Lỗi "Facebook configuration not found for shop: X"

```
jakarta.persistence.EntityNotFoundException: Facebook configuration not found for shop: X
```

Lỗi này xảy ra khi bạn cố gắng kiểm tra trạng thái hoặc sử dụng bot trước khi hoàn thành quá trình cấu hình.

### 2. Lỗi "Field 'is_active' doesn't have a default value"

```
SQL Error: 1364, SQLState: HY000
Field 'is_active' doesn't have a default value
```

Lỗi này liên quan đến sự không khớp giữa tên cột trong database (is_active) và trường trong entity (active).

### 3. Lỗi "Field 'page_id' doesn't have a default value"

```
SQL Error: 1364, SQLState: HY000
Field 'page_id' doesn't have a default value
```

Lỗi này xảy ra khi bạn chưa cung cấp giá trị cho trường page_id khi lưu Facebook access token.

## Nguyên Nhân

### Lỗi 1: Thứ tự gọi API không đúng

API Facebook Bot được thiết kế để hoạt động theo một thứ tự cụ thể:

1. Trước tiên phải cấu hình webhook
2. Sau đó cung cấp access token
3. Cuối cùng mới khởi động bot và kiểm tra trạng thái

Nếu bạn gọi API không đúng thứ tự, lỗi sẽ xảy ra.

### Lỗi 2: Không khớp tên cột trong database

Hệ thống đang tìm kiếm cột `is_active` trong database trong khi entity được định nghĩa với trường `active`. Cần giải quyết vấn đề mapping này.

### Lỗi 3: Thiếu tham số page_id

Khi lưu access token, cần cung cấp thêm page_id của Facebook Page mà token liên kết với. Đây là tham số bắt buộc.

## Cách Khắc Phục

### Giải Quyết Lỗi Database Schema

Trước khi triển khai tích hợp, cần thực hiện một trong hai cách sau:

#### Cách 1: Liên hệ backend để sửa database schema

Yêu cầu team backend cập nhật entity trong code để phù hợp với schema trong database.

#### Cách 2: Thực hiện query SQL trực tiếp để tạo bảng với schema đúng

Nếu bạn có quyền truy cập database, chạy query sau để sửa bảng:

```sql
-- Nếu bảng chưa tồn tại, tạo bảng mới với cấu trúc đúng
CREATE TABLE IF NOT EXISTS facebook_access_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    page_id VARCHAR(255) NOT NULL,
    access_token VARCHAR(2000) NOT NULL,
    verify_token VARCHAR(255),
    webhook_url VARCHAR(255),
    is_active BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_facebook_shop_id (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### Thứ Tự API Đúng

Sau khi giải quyết vấn đề database, hãy tuân theo thứ tự gọi API sau đây:

#### 1. Cấu Hình Webhook

```javascript
// Bước 1: Cấu hình webhook
async function configureWebhook(shopId) {
  try {
    const response = await fetch(`/assistant/api/facebook/shops/${shopId}/configure`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      }
    });
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    
    const data = await response.json();
    console.log('Webhook configured:', data);
    
    // Lưu thông tin này để cấu hình trong Facebook Developer Portal
    return data; // { webhookUrl, verifyToken }
  } catch (error) {
    console.error('Error configuring webhook:', error);
    throw error;
  }
}
```

#### 2. Cung Cấp Access Token và Page ID

```javascript
// Bước 2: Sau khi cấu hình webhook trong Facebook Developer Portal 
// và lấy Page Access Token, lưu token vào hệ thống
async function saveAccessToken(shopId, facebookAccessToken, pageId) {
  try {
    const response = await fetch(
      `/assistant/api/facebook/shops/${shopId}/access-token?accessToken=${encodeURIComponent(facebookAccessToken)}&pageId=${encodeURIComponent(pageId)}`, 
      {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${accessToken}`,
          'Content-Type': 'application/json'
        }
      }
    );
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    
    console.log('Access token saved successfully');
    return true;
  } catch (error) {
    console.error('Error saving access token:', error);
    throw error;
  }
}
```

#### 3. Khởi Động Bot

```javascript
// Bước 3: Khởi động bot sau khi đã lưu access token
async function startBot(shopId) {
  try {
    const response = await fetch(`/assistant/api/facebook/shops/${shopId}/start`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      }
    });
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    
    console.log('Bot started successfully');
    return true;
  } catch (error) {
    console.error('Error starting bot:', error);
    throw error;
  }
}
```

#### 4. Kiểm Tra Trạng Thái

```javascript
// Bước 4: Kiểm tra trạng thái bot
async function checkBotStatus(shopId) {
  try {
    const response = await fetch(`/assistant/api/facebook/shops/${shopId}/status`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      }
    });
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    
    const data = await response.json();
    console.log('Bot status:', data);
    return data; // { shopId, active, webhookUrl }
  } catch (error) {
    console.error('Error checking bot status:', error);
    throw error;
  }
}
```

### Ví Dụ Luồng Hoàn Chỉnh

```javascript
async function setupFacebookBot(shopId, facebookAccessToken, pageId) {
  try {
    // Bước 1: Cấu hình webhook
    const webhookConfig = await configureWebhook(shopId);
    
    // Hiển thị hướng dẫn cho người dùng
    alert(`Vui lòng cấu hình webhook trong Facebook Developer Portal:
    - Webhook URL: ${webhookConfig.webhookUrl}
    - Verify Token: ${webhookConfig.verifyToken}
    - Đăng ký sự kiện: messages, messaging_postbacks
    
    Sau đó lấy Page Access Token và Page ID, rồi nhập vào hệ thống.`);
    
    // Bước 2: Lưu access token và page ID (sau khi người dùng nhập)
    await saveAccessToken(shopId, facebookAccessToken, pageId);
    
    // Bước 3: Khởi động bot
    await startBot(shopId);
    
    // Bước 4: Kiểm tra trạng thái
    const status = await checkBotStatus(shopId);
    
    return status;
  } catch (error) {
    console.error('Error setting up Facebook bot:', error);
    throw error;
  }
}
```

## Giao Diện Người Dùng Đề Xuất

Đây là ví dụ về quy trình UI cho việc thiết lập bot:

1. **Màn hình 1: Bắt đầu cấu hình**
   - Nút "Cấu hình Facebook Bot"
   - Khi nhấp vào, gọi API configureWebhook

2. **Màn hình 2: Hướng dẫn cấu hình webhook**
   - Hiển thị webhookUrl và verifyToken
   - Hướng dẫn người dùng:
     - Truy cập Facebook Developer Portal
     - Tạo ứng dụng (nếu chưa có)
     - Cấu hình webhook với URL và token đã cung cấp
     - Lấy Page Access Token và Page ID
   - Trường nhập Page Access Token
   - Trường nhập Page ID
   - Nút "Tiếp tục" gọi API saveAccessToken

3. **Màn hình 3: Khởi động bot**
   - Thông báo cấu hình thành công
   - Nút "Khởi động Bot" gọi API startBot
   - Sau khi khởi động, hiển thị trạng thái từ API getBotStatus

## Cách Lấy Page ID Facebook

Để lấy Page ID Facebook, bạn có thể:

1. **Từ URL của trang**: Truy cập trang Facebook của bạn, Page ID thường hiển thị trong URL: `https://www.facebook.com/[trang-cua-ban]/` hoặc `https://www.facebook.com/[trang-cua-ban]-[ID-page]/`

2. **Từ Facebook Developer Portal**: 
   - Đi đến trang [Meta for Developers](https://developers.facebook.com/)
   - Chọn ứng dụng của bạn
   - Đi đến phần "Messenger" > "Settings"
   - Trong phần "Access Tokens", bạn sẽ thấy danh sách các trang và ID tương ứng

3. **Từ Facebook Page About**: 
   - Đi đến trang Facebook của bạn
   - Nhấp vào "About" hoặc "Thông tin" ở sidebar
   - Cuộn xuống phần dưới để tìm Page ID

## Xử Lý Lỗi Phổ Biến

1. **Lỗi "Facebook configuration not found"**
   - Đảm bảo đã gọi API configureWebhook trước
   - Kiểm tra shopId đúng

2. **Lỗi khi lưu access token**
   - Kiểm tra access token và page ID hợp lệ
   - Kiểm tra đã cấu hình webhook chưa

3. **Lỗi khi khởi động bot**
   - Đảm bảo đã lưu access token và page ID
   - Kiểm tra access token còn hiệu lực

## Lưu Ý Quan Trọng

- **KHÔNG** gọi API status trước khi hoàn thành cấu hình
- Luôn tuân thủ đúng thứ tự API: configure -> save token và page ID -> start -> status
- Lưu trữ cấu hình webhook (URL, token) để người dùng có thể tham khảo lại sau này
- Cung cấp hướng dẫn trực quan về cách cấu hình trong Facebook Developer Portal
- **LUÔN CUNG CẤP** cả Page ID khi lưu access token để tránh lỗi database 