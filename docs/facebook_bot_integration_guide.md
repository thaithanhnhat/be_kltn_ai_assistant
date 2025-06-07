# Facebook Messenger Bot API - Hướng dẫn Frontend Integration

## Mục lục
1. [Giới thiệu](#giới-thiệu)
2. [Authentication](#authentication)
3. [API Endpoints](#api-endpoints)
4. [Facebook App Setup](#facebook-app-setup)
5. [Webhook Configuration](#webhook-configuration)
6. [Flow Integration](#flow-integration)
7. [Error Handling](#error-handling)
8. [Testing](#testing)

## Giới thiệu

Hệ thống Facebook Messenger Bot cho phép:
- Tích hợp chatbot AI với Facebook Messenger
- Quản lý multiple fanpages cho một shop
- Xử lý tin nhắn tự động và đặt hàng
- Webhook để nhận tin nhắn từ Facebook

## Authentication

### Headers yêu cầu cho Protected Endpoints:
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

### Public Endpoints (không cần token):
- `/api/facebook/webhook/**` - Facebook webhook endpoints

### Protected Endpoints (cần JWT token):
- `/api/facebook-pages/**` - Facebook page management
- `/api/facebook/shops/**` - Facebook bot management

## API Endpoints

### 1. Facebook Bot Management (`/api/facebook`)

#### 1.1 Cấu hình Webhook
```http
POST /api/facebook/shops/{shopId}/configure
```

**Response:**
```json
{
  "webhookUrl": "https://yourdomain.com/api/facebook/webhook/123",
  "verifyToken": "abc123xyz789",
  "pageId": ""
}
```

#### 1.2 Lưu Access Token
```http
POST /api/facebook/shops/{shopId}/access-token
```

**Parameters:**
- `accessToken` (string): Facebook Page Access Token
- `pageId` (string): Facebook Page ID

**Response:** `200 OK`

#### 1.3 Khởi động Bot
```http
POST /api/facebook/shops/{shopId}/start
```

**Response:** `200 OK`

#### 1.4 Dừng Bot
```http
POST /api/facebook/shops/{shopId}/stop
```

**Response:** `200 OK`

#### 1.5 Kiểm tra trạng thái Bot
```http
GET /api/facebook/shops/{shopId}/status
```

**Response:**
```json
{
  "shopId": 123,
  "active": true,
  "webhookUrl": "https://yourdomain.com/api/facebook/webhook/123",
  "pageId": "1234567890",
  "hasAccessToken": true,
  "verifyToken": "abc123xyz789"
}
```

#### 1.6 Gửi tin nhắn thủ công
```http
POST /api/facebook/shops/{shopId}/send
```

**Parameters:**
- `recipientId` (string): Facebook User ID
- `message` (string): Nội dung tin nhắn

**Response:** `200 OK`

### 2. Facebook Page Management (`/api/facebook-pages`)

#### 2.1 Thêm Fanpage mới
```http
POST /api/facebook-pages/shops/{shopId}/pages
```

**Request Body:**
```json
{
  "pageId": "1234567890",
  "pageName": "My Shop Fanpage",
  "accessToken": "page_access_token_here",
  "verifyToken": "custom_verify_token",
  "webhookUrl": "https://yourdomain.com/api/facebook/webhook/123",
  "subscribedEvents": ["messages", "messaging_postbacks"]
}
```

**Response:**
```json
{
  "message": "Facebook page configured successfully",
  "pageId": "1234567890",
  "shopId": "123"
}
```

#### 2.2 Lấy danh sách Fanpages
```http
GET /api/facebook-pages/shops/{shopId}/pages
```

**Response:**
```json
[
  {
    "pageId": "1234567890",
    "pageName": "My Shop Fanpage",
    "webhookUrl": "https://yourdomain.com/api/facebook/webhook/123",
    "verifyToken": "custom_verify_token",
    "subscribedEvents": ["messages", "messaging_postbacks"],
    "active": true
  }
]
```

#### 2.3 Lấy thông tin Fanpage cụ thể
```http
GET /api/facebook-pages/shops/{shopId}/pages/{pageId}
```

**Response:**
```json
{
  "pageId": "1234567890",
  "pageName": "My Shop Fanpage",
  "webhookUrl": "https://yourdomain.com/api/facebook/webhook/123",
  "verifyToken": "custom_verify_token",
  "subscribedEvents": ["messages", "messaging_postbacks"],
  "active": true
}
```

#### 2.4 Cập nhật Fanpage
```http
PUT /api/facebook-pages/shops/{shopId}/pages/{pageId}
```

**Request Body:**
```json
{
  "pageName": "Updated Page Name",
  "subscribedEvents": ["messages", "messaging_postbacks"],
  "active": true
}
```

**Response:**
```json
{
  "message": "Facebook page configuration updated successfully",
  "pageId": "1234567890",
  "shopId": "123"
}
```

#### 2.5 Xóa Fanpage
```http
DELETE /api/facebook-pages/shops/{shopId}/pages/{pageId}
```

**Response:**
```json
{
  "message": "Facebook page removed successfully",
  "pageId": "1234567890",
  "shopId": "123"
}
```

#### 2.6 Subscribe Webhook
```http
POST /api/facebook-pages/shops/{shopId}/pages/{pageId}/subscribe
```

**Response:**
```json
{
  "message": "Facebook page subscribed to webhook successfully",
  "pageId": "1234567890",
  "shopId": "123"
}
```

#### 2.7 Unsubscribe Webhook
```http
POST /api/facebook-pages/shops/{shopId}/pages/{pageId}/unsubscribe
```

**Response:**
```json
{
  "message": "Facebook page unsubscribed from webhook successfully",
  "pageId": "1234567890",
  "shopId": "123"
}
```

#### 2.8 Gửi tin nhắn test
```http
POST /api/facebook-pages/shops/{shopId}/pages/{pageId}/test-message
```

**Request Body:**
```json
{
  "testUserId": "facebook_user_id",
  "message": "Hello! This is a test message."
}
```

**Response:**
```json
{
  "message": "Test message sent successfully",
  "pageId": "1234567890",
  "shopId": "123",
  "recipientId": "facebook_user_id"
}
```

## Facebook App Setup

### 1. Tạo Facebook App
1. Vào [Facebook Developers](https://developers.facebook.com/)
2. Tạo App mới chọn type "Business"
3. Thêm "Messenger" product
4. Lấy App ID và App Secret

### 2. Cấu hình Webhook trong Facebook App
1. Vào Messenger → Settings → Webhooks
2. Click "Add Callback URL"
3. **Callback URL**: Sử dụng `webhookUrl` từ API response
4. **Verify Token**: Sử dụng `verifyToken` từ API response
5. **Subscription Fields**: Chọn `messages` và `messaging_postbacks`
6. Click "Verify and Save"

### 3. Lấy Page Access Token
1. Vào Messenger → Settings → Access Tokens
2. Chọn fanpage cần tích hợp
3. Generate Access Token
4. Copy token để sử dụng trong API

## Webhook Configuration

### Automatic Webhook Setup (Recommended)
Sử dụng API để tự động cấu hình:

```javascript
// 1. Cấu hình webhook
const webhookConfig = await fetch('/api/facebook/shops/123/configure', {
  method: 'POST',
  headers: { 'Authorization': 'Bearer ' + token }
});
const config = await webhookConfig.json();

// 2. Sử dụng config.webhookUrl và config.verifyToken trong Facebook App Dashboard
console.log('Webhook URL:', config.webhookUrl);
console.log('Verify Token:', config.verifyToken);

// 3. Lưu access token
await fetch('/api/facebook/shops/123/access-token', {
  method: 'POST',
  headers: { 'Authorization': 'Bearer ' + token },
  body: new URLSearchParams({
    accessToken: 'YOUR_PAGE_ACCESS_TOKEN',
    pageId: 'YOUR_PAGE_ID'
  })
});

// 4. Khởi động bot
await fetch('/api/facebook/shops/123/start', {
  method: 'POST',
  headers: { 'Authorization': 'Bearer ' + token }
});
```

## Flow Integration

### 1. Flow cơ bản (Single Fanpage)
```javascript
async function setupFacebookBot(shopId, pageAccessToken, pageId) {
  try {
    // Bước 1: Cấu hình webhook
    const configResponse = await fetch(`/api/facebook/shops/${shopId}/configure`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${authToken}` }
    });
    const config = await configResponse.json();
    
    console.log('Configure webhook in Facebook App:');
    console.log('Webhook URL:', config.webhookUrl);
    console.log('Verify Token:', config.verifyToken);
    
    // Bước 2: Đợi user cấu hình webhook trong Facebook App
    alert(`Please configure webhook in Facebook App:
    Webhook URL: ${config.webhookUrl}
    Verify Token: ${config.verifyToken}`);
    
    // Bước 3: Lưu access token
    await fetch(`/api/facebook/shops/${shopId}/access-token`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${authToken}` },
      body: new URLSearchParams({
        accessToken: pageAccessToken,
        pageId: pageId
      })
    });
    
    // Bước 4: Khởi động bot
    await fetch(`/api/facebook/shops/${shopId}/start`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${authToken}` }
    });
    
    // Bước 5: Kiểm tra trạng thái
    const statusResponse = await fetch(`/api/facebook/shops/${shopId}/status`, {
      headers: { 'Authorization': `Bearer ${authToken}` }
    });
    const status = await statusResponse.json();
    
    console.log('Bot Status:', status);
    return status;
    
  } catch (error) {
    console.error('Setup failed:', error);
    throw error;
  }
}
```

### 2. Flow Multiple Fanpages
```javascript
async function setupMultipleFanpages(shopId, pages) {
  try {
    const results = [];
    
    for (const page of pages) {
      // Thêm từng fanpage
      const addResponse = await fetch(`/api/facebook-pages/shops/${shopId}/pages`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${authToken}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          pageId: page.pageId,
          pageName: page.pageName,
          accessToken: page.accessToken,
          verifyToken: generateVerifyToken(),
          webhookUrl: `https://yourdomain.com/api/facebook/webhook/${shopId}`,
          subscribedEvents: ['messages', 'messaging_postbacks']
        })
      });
      
      if (addResponse.ok) {
        // Subscribe to webhook
        await fetch(`/api/facebook-pages/shops/${shopId}/pages/${page.pageId}/subscribe`, {
          method: 'POST',
          headers: { 'Authorization': `Bearer ${authToken}` }
        });
        
        results.push({ pageId: page.pageId, status: 'success' });
      } else {
        results.push({ pageId: page.pageId, status: 'failed' });
      }
    }
    
    // Lấy danh sách để confirm
    const listResponse = await fetch(`/api/facebook-pages/shops/${shopId}/pages`, {
      headers: { 'Authorization': `Bearer ${authToken}` }
    });
    const configuredPages = await listResponse.json();
    
    return { results, configuredPages };
    
  } catch (error) {
    console.error('Multiple fanpages setup failed:', error);
    throw error;
  }
}

function generateVerifyToken() {
  return Math.random().toString(36).substring(2, 15) + 
         Math.random().toString(36).substring(2, 15);
}
```

### 3. Real-time Status Monitoring
```javascript
async function monitorBotStatus(shopId) {
  setInterval(async () => {
    try {
      const response = await fetch(`/api/facebook/shops/${shopId}/status`, {
        headers: { 'Authorization': `Bearer ${authToken}` }
      });
      const status = await response.json();
      
      // Update UI with current status
      updateBotStatusUI(status);
      
    } catch (error) {
      console.error('Status check failed:', error);
    }
  }, 30000); // Check every 30 seconds
}

function updateBotStatusUI(status) {
  document.getElementById('bot-active').textContent = status.active ? 'Active' : 'Inactive';
  document.getElementById('page-id').textContent = status.pageId || 'Not configured';
  document.getElementById('has-token').textContent = status.hasAccessToken ? 'Yes' : 'No';
}
```

## Error Handling

### Common Error Responses
```javascript
// 400 Bad Request
{
  "error": "Failed to configure Facebook page",
  "message": "Invalid page access token"
}

// 404 Not Found
{
  "error": "Facebook configuration not found for shop: 123"
}

// 500 Internal Server Error
{
  "error": "Failed to subscribe page to webhook",
  "message": "Connection timeout to Facebook API"
}
```

### Error Handling Implementation
```javascript
async function handleFacebookAPI(url, options) {
  try {
    const response = await fetch(url, options);
    
    if (!response.ok) {
      const errorData = await response.json();
      throw new Error(`${response.status}: ${errorData.message || errorData.error}`);
    }
    
    return await response.json();
    
  } catch (error) {
    console.error('Facebook API Error:', error);
    
    // Handle specific errors
    if (error.message.includes('Invalid page access token')) {
      alert('Page access token không hợp lệ. Vui lòng kiểm tra lại.');
    } else if (error.message.includes('already configured')) {
      alert('Fanpage này đã được cấu hình cho shop khác.');
    } else {
      alert('Có lỗi xảy ra: ' + error.message);
    }
    
    throw error;
  }
}
```

## Testing

### 1. Test Webhook Connection
```javascript
// Gửi tin nhắn test
async function testFacebookConnection(shopId, testUserId) {
  try {
    await fetch(`/api/facebook/shops/${shopId}/send`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${authToken}` },
      body: new URLSearchParams({
        recipientId: testUserId,
        message: 'Test message from your AI assistant!'
      })
    });
    
    console.log('Test message sent successfully');
  } catch (error) {
    console.error('Test failed:', error);
  }
}
```

### 2. Test Multiple Pages
```javascript
async function testMultiplePages(shopId, pageId, testUserId) {
  try {
    const response = await fetch(`/api/facebook-pages/shops/${shopId}/pages/${pageId}/test-message`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${authToken}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        testUserId: testUserId,
        message: 'Test message from specific fanpage!'
      })
    });
    
    const result = await response.json();
    console.log('Test result:', result);
    
  } catch (error) {
    console.error('Multi-page test failed:', error);
  }
}
```

### 3. Validate Configuration
```javascript
async function validateFacebookSetup(shopId) {
  try {
    // Check bot status
    const status = await fetch(`/api/facebook/shops/${shopId}/status`, {
      headers: { 'Authorization': `Bearer ${authToken}` }
    }).then(r => r.json());
    
    // Check pages
    const pages = await fetch(`/api/facebook-pages/shops/${shopId}/pages`, {
      headers: { 'Authorization': `Bearer ${authToken}` }
    }).then(r => r.json());
    
    const validation = {
      botActive: status.active,
      hasAccessToken: status.hasAccessToken,
      pagesCount: pages.length,
      activePagesCount: pages.filter(p => p.active).length,
      issues: []
    };
    
    if (!validation.botActive) {
      validation.issues.push('Bot is not active');
    }
    
    if (!validation.hasAccessToken) {
      validation.issues.push('No access token configured');
    }
    
    if (validation.pagesCount === 0) {
      validation.issues.push('No pages configured');
    }
    
    return validation;
    
  } catch (error) {
    console.error('Validation failed:', error);
    return { valid: false, error: error.message };
  }
}
```

## Notes quan trọng

1. **Access Token Security**: Không lưu access token trong localStorage, chỉ gửi qua API
2. **Webhook URL**: Phải là HTTPS và publicly accessible
3. **Rate Limiting**: Facebook có giới hạn API calls, implement retry logic
4. **Page Permissions**: Access token cần quyền `pages_messaging`
5. **Verify Token**: Mỗi shop/page nên có verify token riêng
6. **Error Monitoring**: Log và monitor các lỗi để troubleshoot
7. **Multiple Pages**: Một fanpage chỉ có thể thuộc về một shop
8. **AI Integration**: Bot tự động xử lý tin nhắn và đặt hàng thông qua AI
9. **Address Command**: User có thể dùng `/address [địa chỉ]` để cập nhật địa chỉ giao hàng

## Support Commands for Users

Hệ thống hỗ trợ các lệnh sau cho người dùng:

- **Tin nhắn thường**: AI sẽ tự động trả lời và xử lý
- **`/address [địa chỉ đầy đủ]`**: Cập nhật địa chỉ giao hàng
- **Đặt hàng**: AI tự động detect và xử lý đặt hàng
- **Xem sản phẩm**: AI có thể hiển thị thông tin sản phẩm
