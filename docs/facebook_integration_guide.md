# Hướng dẫn tích hợp Facebook Messenger cho Shop

## Tổng quan
Hướng dẫn này mô tả cách thêm Facebook Page vào shop và tích hợp chatbot AI với Facebook Messenger.

## Luồng tích hợp

### 1. Tạo và cấu hình Facebook App

#### Bước 1: Tạo Facebook App
1. Truy cập https://developers.facebook.com/
2. Tạo ứng dụng mới với loại "Business"
3. Thêm sản phẩm "Messenger" vào ứng dụng

#### Bước 2: Cấu hình Messenger
1. Trong Messenger Settings, thêm Facebook Page
2. Tạo Page Access Token
3. Cấu hình Webhook

### 2. Backend API Endpoints

#### A. Cấu hình Webhook
```http
POST /assistant/api/facebook/shops/{shopId}/configure
```

**Response:**
```json
{
  "webhookUrl": "https://4dba-116-98-249-135.ngrok-free.app/assistant/api/facebook/webhook/123",
  "verifyToken": "abc123xyz789",
  "pageId": ""
}
```

#### B. Lưu Access Token và Page ID
```http
POST /assistant/api/facebook/shops/{shopId}/access-token
Content-Type: application/x-www-form-urlencoded

accessToken=YOUR_PAGE_ACCESS_TOKEN&pageId=YOUR_PAGE_ID
```

#### C. Khởi động Bot
```http
POST /assistant/api/facebook/shops/{shopId}/start
```

#### D. Kiểm tra trạng thái
```http
GET /assistant/api/facebook/shops/{shopId}/status
```

**Response:**
```json
{
  "shopId": 123,
  "active": true,
  "webhookUrl": "https://4dba-116-98-249-135.ngrok-free.app/assistant/api/facebook/webhook/123"
}
```

#### E. Dừng Bot
```http
POST /assistant/api/facebook/shops/{shopId}/stop
```

### 3. Frontend Implementation

#### A. Component Structure
```javascript
// FacebookIntegration.jsx
import React, { useState, useEffect } from 'react';

const FacebookIntegration = ({ shopId }) => {
  const [webhookConfig, setWebhookConfig] = useState(null);
  const [pageAccessToken, setPageAccessToken] = useState('');
  const [pageId, setPageId] = useState('');
  const [botStatus, setBotStatus] = useState(null);
  
  // Component logic here
};
```

#### B. API Integration Functions
```javascript
// facebookApi.js
const API_BASE = '/assistant/api/facebook';

export const facebookApi = {
  // Cấu hình webhook
  configureWebhook: async (shopId) => {
    const response = await fetch(`${API_BASE}/shops/${shopId}/configure`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${getAuthToken()}`,
        'Content-Type': 'application/json'
      }
    });
    return response.json();
  },

  // Lưu access token
  saveAccessToken: async (shopId, accessToken, pageId) => {
    const formData = new URLSearchParams();
    formData.append('accessToken', accessToken);
    formData.append('pageId', pageId);
    
    const response = await fetch(`${API_BASE}/shops/${shopId}/access-token`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${getAuthToken()}`,
        'Content-Type': 'application/x-www-form-urlencoded'
      },
      body: formData
    });
    return response.ok;
  },

  // Khởi động bot
  startBot: async (shopId) => {
    const response = await fetch(`${API_BASE}/shops/${shopId}/start`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${getAuthToken()}`
      }
    });
    return response.ok;
  },

  // Kiểm tra trạng thái
  getBotStatus: async (shopId) => {
    const response = await fetch(`${API_BASE}/shops/${shopId}/status`, {
      headers: {
        'Authorization': `Bearer ${getAuthToken()}`
      }
    });
    return response.json();
  },

  // Dừng bot
  stopBot: async (shopId) => {
    const response = await fetch(`${API_BASE}/shops/${shopId}/stop`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${getAuthToken()}`
      }
    });
    return response.ok;
  }
};
```

#### C. Complete React Component
```javascript
// FacebookIntegration.jsx
import React, { useState, useEffect } from 'react';
import { facebookApi } from './api/facebookApi';

const FacebookIntegration = ({ shopId }) => {
  const [loading, setLoading] = useState(false);
  const [webhookConfig, setWebhookConfig] = useState(null);
  const [pageAccessToken, setPageAccessToken] = useState('');
  const [pageId, setPageId] = useState('');
  const [botStatus, setBotStatus] = useState(null);
  const [step, setStep] = useState(1);

  useEffect(() => {
    loadBotStatus();
  }, [shopId]);

  const loadBotStatus = async () => {
    try {
      const status = await facebookApi.getBotStatus(shopId);
      setBotStatus(status);
      if (status.active) {
        setStep(4); // Bot đã hoạt động
      }
    } catch (error) {
      console.error('Error loading bot status:', error);
    }
  };

  const handleConfigureWebhook = async () => {
    setLoading(true);
    try {
      const config = await facebookApi.configureWebhook(shopId);
      setWebhookConfig(config);
      setStep(2);
    } catch (error) {
      alert('Lỗi cấu hình webhook: ' + error.message);
    } finally {
      setLoading(false);
    }
  };

  const handleSaveAccessToken = async () => {
    if (!pageAccessToken || !pageId) {
      alert('Vui lòng nhập đầy đủ Page Access Token và Page ID');
      return;
    }

    setLoading(true);
    try {
      const success = await facebookApi.saveAccessToken(shopId, pageAccessToken, pageId);
      if (success) {
        setStep(3);
      } else {
        alert('Lỗi lưu access token');
      }
    } catch (error) {
      alert('Lỗi lưu access token: ' + error.message);
    } finally {
      setLoading(false);
    }
  };

  const handleStartBot = async () => {
    setLoading(true);
    try {
      const success = await facebookApi.startBot(shopId);
      if (success) {
        setStep(4);
        await loadBotStatus();
        alert('Bot Facebook đã được khởi động thành công!');
      } else {
        alert('Lỗi khởi động bot');
      }
    } catch (error) {
      alert('Lỗi khởi động bot: ' + error.message);
    } finally {
      setLoading(false);
    }
  };

  const handleStopBot = async () => {
    setLoading(true);
    try {
      const success = await facebookApi.stopBot(shopId);
      if (success) {
        await loadBotStatus();
        alert('Bot Facebook đã được dừng!');
      } else {
        alert('Lỗi dừng bot');
      }
    } catch (error) {
      alert('Lỗi dừng bot: ' + error.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="facebook-integration">
      <h2>Tích hợp Facebook Messenger</h2>
      
      {/* Step 1: Configure Webhook */}
      {step >= 1 && (
        <div className="step">
          <h3>Bước 1: Cấu hình Webhook</h3>
          {!webhookConfig ? (
            <button 
              onClick={handleConfigureWebhook} 
              disabled={loading}
              className="btn btn-primary"
            >
              {loading ? 'Đang cấu hình...' : 'Cấu hình Webhook'}
            </button>
          ) : (
            <div className="webhook-info">
              <p><strong>Webhook URL:</strong></p>
              <code>{webhookConfig.webhookUrl}</code>
              <p><strong>Verify Token:</strong></p>
              <code>{webhookConfig.verifyToken}</code>
              <div className="alert alert-info">
                <p>Sao chép thông tin trên và cấu hình trong Facebook App:</p>
                <ol>
                  <li>Truy cập Facebook Developers Console</li>
                  <li>Vào Messenger → Settings → Webhooks</li>
                  <li>Nhập Webhook URL và Verify Token</li>
                  <li>Subscribe to: messages, messaging_postbacks</li>
                </ol>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Step 2: Page Access Token */}
      {step >= 2 && (
        <div className="step">
          <h3>Bước 2: Nhập Page Access Token</h3>
          <div className="form-group">
            <label>Page Access Token:</label>
            <input
              type="text"
              value={pageAccessToken}
              onChange={(e) => setPageAccessToken(e.target.value)}
              placeholder="Nhập Page Access Token từ Facebook"
              className="form-control"
            />
          </div>
          <div className="form-group">
            <label>Page ID:</label>
            <input
              type="text"
              value={pageId}
              onChange={(e) => setPageId(e.target.value)}
              placeholder="Nhập Page ID"
              className="form-control"
            />
          </div>
          <button 
            onClick={handleSaveAccessToken} 
            disabled={loading}
            className="btn btn-primary"
          >
            {loading ? 'Đang lưu...' : 'Lưu Access Token'}
          </button>
          <div className="alert alert-info">
            <p>Để lấy Page Access Token:</p>
            <ol>
              <li>Vào Facebook Developers Console</li>
              <li>Messenger → Settings → Access Tokens</li>
              <li>Chọn Page và Generate Token</li>
            </ol>
          </div>
        </div>
      )}

      {/* Step 3: Start Bot */}
      {step >= 3 && (
        <div className="step">
          <h3>Bước 3: Khởi động Bot</h3>
          <button 
            onClick={handleStartBot} 
            disabled={loading}
            className="btn btn-success"
          >
            {loading ? 'Đang khởi động...' : 'Khởi động Bot Facebook'}
          </button>
        </div>
      )}

      {/* Step 4: Bot Status */}
      {step >= 4 && botStatus && (
        <div className="step">
          <h3>Trạng thái Bot</h3>
          <div className={`status ${botStatus.active ? 'active' : 'inactive'}`}>
            <p><strong>Trạng thái:</strong> {botStatus.active ? 'Đang hoạt động' : 'Đã dừng'}</p>
            <p><strong>Webhook URL:</strong> {botStatus.webhookUrl}</p>
          </div>
          {botStatus.active ? (
            <button 
              onClick={handleStopBot} 
              disabled={loading}
              className="btn btn-danger"
            >
              {loading ? 'Đang dừng...' : 'Dừng Bot'}
            </button>
          ) : (
            <button 
              onClick={handleStartBot} 
              disabled={loading}
              className="btn btn-success"
            >
              {loading ? 'Đang khởi động...' : 'Khởi động Bot'}
            </button>
          )}
        </div>
      )}
    </div>
  );
};

export default FacebookIntegration;
```

#### D. CSS Styles
```css
/* FacebookIntegration.css */
.facebook-integration {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}

.step {
  margin-bottom: 30px;
  padding: 20px;
  border: 1px solid #ddd;
  border-radius: 8px;
  background-color: #f9f9f9;
}

.webhook-info {
  margin-top: 15px;
}

.webhook-info code {
  display: block;
  background: #f4f4f4;
  padding: 10px;
  border-radius: 4px;
  margin: 5px 0 15px 0;
  word-break: break-all;
}

.form-group {
  margin-bottom: 15px;
}

.form-group label {
  display: block;
  margin-bottom: 5px;
  font-weight: bold;
}

.form-control {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
}

.btn {
  padding: 10px 20px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  margin-right: 10px;
}

.btn-primary {
  background-color: #007bff;
  color: white;
}

.btn-success {
  background-color: #28a745;
  color: white;
}

.btn-danger {
  background-color: #dc3545;
  color: white;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.alert {
  padding: 15px;
  margin: 15px 0;
  border-radius: 4px;
}

.alert-info {
  background-color: #d1ecf1;
  border: 1px solid #bee5eb;
  color: #0c5460;
}

.status.active {
  color: #28a745;
}

.status.inactive {
  color: #dc3545;
}
```

### 4. Sử dụng trong ứng dụng

```javascript
// App.jsx hoặc ShopManagement.jsx
import React from 'react';
import FacebookIntegration from './components/FacebookIntegration';

const ShopManagement = () => {
  const shopId = 123; // Lấy từ state hoặc props

  return (
    <div className="shop-management">
      <h1>Quản lý Shop</h1>
      
      <div className="integrations">
        <FacebookIntegration shopId={shopId} />
      </div>
    </div>
  );
};

export default ShopManagement;
```

### 5. Lưu ý quan trọng

#### A. Bảo mật
- Luôn lưu trữ Access Token một cách an toàn
- Sử dụng HTTPS cho tất cả webhook URLs
- Xác thực người dùng trước khi cho phép cấu hình

#### B. Error Handling
- Implement retry logic cho API calls
- Hiển thị thông báo lỗi rõ ràng cho người dùng
- Log errors để debug

#### C. Testing
- Test webhook với ngrok trước khi deploy
- Verify rằng AI chatbot hoạt động đúng
- Test các tính năng như đặt hàng, cập nhật địa chỉ

### 6. Webhook Events

Bot sẽ xử lý các loại message sau:

#### A. Text Messages
- Chat thông thường với AI
- Commands như `/address <địa chỉ>`

#### B. AI Actions
- Hiển thị thông tin sản phẩm
- Tạo đơn hàng
- Cập nhật địa chỉ khách hàng

### 7. Troubleshooting

#### Lỗi thường gặp:
1. **Webhook verification failed**: Kiểm tra verify token
2. **Access token invalid**: Refresh page access token
3. **Bot not responding**: Kiểm tra webhook subscription
4. **Permission denied**: Đảm bảo page permissions được cấp đúng

#### Debug steps:
1. Kiểm tra logs trong browser console
2. Verify webhook URL accessible từ Facebook
3. Test API endpoints với Postman
4. Kiểm tra database cho configuration records

## Kết luận

Hướng dẫn này cung cấp đầy đủ thông tin để tích hợp Facebook Messenger vào hệ thống shop. Frontend có thể sử dụng các API endpoints và React components để tạo trải nghiệm người dùng hoàn chỉnh.
