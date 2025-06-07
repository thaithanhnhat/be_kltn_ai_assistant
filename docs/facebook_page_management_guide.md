# Hướng dẫn quản lý Facebook Page cho Shop

## Tổng quan
Hướng dẫn này mô tả cách thêm, quản lý và tích hợp Facebook Page vào hệ thống shop, bao gồm cả frontend implementation và API documentation.

## Kiến trúc hệ thống

### 1. Facebook Integration Flow
```
1. Shop Owner → Cấu hình Webhook → Hệ thống tạo webhook URL
2. Shop Owner → Thêm Facebook App → Facebook Developers Console  
3. Shop Owner → Lấy Page Access Token → Facebook Developers Console
4. Shop Owner → Lưu Access Token → Hệ thống lưu vào database
5. Shop Owner → Khởi động Bot → Bot bắt đầu nhận tin nhắn
6. Customer → Gửi tin nhắn → Facebook → Webhook → AI Bot → Phản hồi
```

### 2. Database Schema
```sql
-- Facebook Access Tokens Table
CREATE TABLE facebook_access_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    shop_id BIGINT NOT NULL,
    page_id VARCHAR(255),
    access_token VARCHAR(2000) NOT NULL,
    verify_token VARCHAR(255),
    webhook_url VARCHAR(500),
    is_active BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Access Tokens Table (Generic)
CREATE TABLE access_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    shop_id BIGINT NOT NULL,
    access_token VARCHAR(512) NOT NULL,
    status ENUM('ACTIVE', 'EXPIRED', 'REVOKED') NOT NULL,
    method ENUM('TELEGRAM', 'FACEBOOK') NOT NULL
);
```

## API Endpoints

### 1. Facebook Bot Management

#### A. Cấu hình Webhook
```http
POST /assistant/api/facebook/shops/{shopId}/configure
Authorization: Bearer {JWT_TOKEN}
```

**Response:**
```json
{
  "webhookUrl": "https://4dba-116-98-249-135.ngrok-free.app/assistant/api/facebook/webhook/123",
  "verifyToken": "abc123xyz789",
  "pageId": ""
}
```

#### B. Lưu Page Access Token
```http
POST /assistant/api/facebook/shops/{shopId}/access-token
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/x-www-form-urlencoded

accessToken=YOUR_PAGE_ACCESS_TOKEN&pageId=YOUR_PAGE_ID
```

#### C. Khởi động Bot
```http
POST /assistant/api/facebook/shops/{shopId}/start
Authorization: Bearer {JWT_TOKEN}
```

#### D. Dừng Bot  
```http
POST /assistant/api/facebook/shops/{shopId}/stop
Authorization: Bearer {JWT_TOKEN}
```

#### E. Kiểm tra trạng thái
```http
GET /assistant/api/facebook/shops/{shopId}/status
Authorization: Bearer {JWT_TOKEN}
```

**Response:**
```json
{
  "shopId": 123,
  "active": true,
  "webhookUrl": "https://4dba-116-98-249-135.ngrok-free.app/assistant/api/facebook/webhook/123"
}
```

### 2. Access Token Management

#### A. Thêm Access Token (Generic)
```http
POST /assistant/api/integration/tokens
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json

{
  "accessToken": "YOUR_FACEBOOK_PAGE_ACCESS_TOKEN",
  "method": "FACEBOOK",
  "shopId": 123
}
```

#### B. Lấy tất cả tokens của shop
```http
GET /assistant/api/integration/tokens/shop/{shopId}
Authorization: Bearer {JWT_TOKEN}
```

#### C. Lấy tokens theo method
```http
GET /assistant/api/integration/tokens/method/FACEBOOK
Authorization: Bearer {JWT_TOKEN}
```

#### D. Lấy tokens của shop theo method
```http
GET /assistant/api/integration/tokens/shop/{shopId}/method/FACEBOOK
Authorization: Bearer {JWT_TOKEN}
```

#### E. Cập nhật trạng thái token
```http
PATCH /assistant/api/integration/tokens/{tokenId}/status
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json

{
  "status": "ACTIVE" // ACTIVE, EXPIRED, REVOKED
}
```

#### F. Xóa token
```http
DELETE /assistant/api/integration/tokens/{tokenId}
Authorization: Bearer {JWT_TOKEN}
```

## Frontend Implementation

### 1. API Integration Layer

#### facebookPageApi.js
```javascript
// facebookPageApi.js
const API_BASE = '/assistant/api';

// Helper function to get auth token
const getAuthToken = () => {
  return localStorage.getItem('authToken') || sessionStorage.getItem('authToken');
};

export const facebookPageApi = {
  // Facebook Bot Management
  facebook: {
    configureWebhook: async (shopId) => {
      const response = await fetch(`${API_BASE}/facebook/shops/${shopId}/configure`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${getAuthToken()}`,
          'Content-Type': 'application/json'
        }
      });
      if (!response.ok) throw new Error('Failed to configure webhook');
      return response.json();
    },

    saveAccessToken: async (shopId, accessToken, pageId) => {
      const formData = new URLSearchParams();
      formData.append('accessToken', accessToken);
      formData.append('pageId', pageId);
      
      const response = await fetch(`${API_BASE}/facebook/shops/${shopId}/access-token`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${getAuthToken()}`,
          'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: formData
      });
      return response.ok;
    },

    startBot: async (shopId) => {
      const response = await fetch(`${API_BASE}/facebook/shops/${shopId}/start`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${getAuthToken()}`
        }
      });
      return response.ok;
    },

    stopBot: async (shopId) => {
      const response = await fetch(`${API_BASE}/facebook/shops/${shopId}/stop`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${getAuthToken()}`
        }
      });
      return response.ok;
    },

    getBotStatus: async (shopId) => {
      const response = await fetch(`${API_BASE}/facebook/shops/${shopId}/status`, {
        headers: {
          'Authorization': `Bearer ${getAuthToken()}`
        }
      });
      if (!response.ok) throw new Error('Failed to get bot status');
      return response.json();
    }
  },

  // Generic Token Management
  tokens: {
    addToken: async (tokenData) => {
      const response = await fetch(`${API_BASE}/integration/tokens`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${getAuthToken()}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(tokenData)
      });
      if (!response.ok) throw new Error('Failed to add token');
      return response.json();
    },

    getShopTokens: async (shopId) => {
      const response = await fetch(`${API_BASE}/integration/tokens/shop/${shopId}`, {
        headers: {
          'Authorization': `Bearer ${getAuthToken()}`
        }
      });
      if (!response.ok) throw new Error('Failed to get shop tokens');
      return response.json();
    },

    getFacebookTokens: async () => {
      const response = await fetch(`${API_BASE}/integration/tokens/method/FACEBOOK`, {
        headers: {
          'Authorization': `Bearer ${getAuthToken()}`
        }
      });
      if (!response.ok) throw new Error('Failed to get Facebook tokens');
      return response.json();
    },

    getShopFacebookTokens: async (shopId) => {
      const response = await fetch(`${API_BASE}/integration/tokens/shop/${shopId}/method/FACEBOOK`, {
        headers: {
          'Authorization': `Bearer ${getAuthToken()}`
        }
      });
      if (!response.ok) throw new Error('Failed to get shop Facebook tokens');
      return response.json();
    },

    updateTokenStatus: async (tokenId, status) => {
      const response = await fetch(`${API_BASE}/integration/tokens/${tokenId}/status`, {
        method: 'PATCH',
        headers: {
          'Authorization': `Bearer ${getAuthToken()}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ status })
      });
      if (!response.ok) throw new Error('Failed to update token status');
      return response.json();
    },

    deleteToken: async (tokenId) => {
      const response = await fetch(`${API_BASE}/integration/tokens/${tokenId}`, {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${getAuthToken()}`
        }
      });
      return response.ok;
    }
  }
};
```

### 2. React Components

#### FacebookPageManager.jsx
```javascript
// FacebookPageManager.jsx
import React, { useState, useEffect } from 'react';
import { facebookPageApi } from '../api/facebookPageApi';
import './FacebookPageManager.css';

const FacebookPageManager = ({ shopId }) => {
  const [loading, setLoading] = useState(false);
  const [currentStep, setCurrentStep] = useState(1);
  
  // Webhook configuration
  const [webhookConfig, setWebhookConfig] = useState(null);
  
  // Page details
  const [pageAccessToken, setPageAccessToken] = useState('');
  const [pageId, setPageId] = useState('');
  const [pageName, setPageName] = useState('');
  
  // Bot status
  const [botStatus, setBotStatus] = useState(null);
  
  // Tokens management
  const [facebookTokens, setFacebookTokens] = useState([]);
  const [selectedToken, setSelectedToken] = useState(null);

  useEffect(() => {
    loadInitialData();
  }, [shopId]);

  const loadInitialData = async () => {
    try {
      // Load bot status
      await loadBotStatus();
      
      // Load existing tokens
      await loadFacebookTokens();
    } catch (error) {
      console.error('Error loading initial data:', error);
    }
  };

  const loadBotStatus = async () => {
    try {
      const status = await facebookPageApi.facebook.getBotStatus(shopId);
      setBotStatus(status);
      
      if (status.active) {
        setCurrentStep(4); // Bot is already active
      }
    } catch (error) {
      console.error('Error loading bot status:', error);
      // Bot not configured yet, start from step 1
      setCurrentStep(1);
    }
  };

  const loadFacebookTokens = async () => {
    try {
      const tokens = await facebookPageApi.tokens.getShopFacebookTokens(shopId);
      setFacebookTokens(tokens);
    } catch (error) {
      console.error('Error loading Facebook tokens:', error);
    }
  };

  // Step 1: Configure Webhook
  const handleConfigureWebhook = async () => {
    setLoading(true);
    try {
      const config = await facebookPageApi.facebook.configureWebhook(shopId);
      setWebhookConfig(config);
      setCurrentStep(2);
    } catch (error) {
      alert('Lỗi cấu hình webhook: ' + error.message);
    } finally {
      setLoading(false);
    }
  };

  // Step 2: Save Page Access Token  
  const handleSavePageToken = async () => {
    if (!pageAccessToken || !pageId) {
      alert('Vui lòng nhập đầy đủ Page Access Token và Page ID');
      return;
    }

    setLoading(true);
    try {
      // Save via Facebook API
      const facebookSuccess = await facebookPageApi.facebook.saveAccessToken(
        shopId, 
        pageAccessToken, 
        pageId
      );

      if (facebookSuccess) {
        // Also save via generic tokens API for unified management
        await facebookPageApi.tokens.addToken({
          accessToken: pageAccessToken,
          method: 'FACEBOOK',
          shopId: shopId
        });

        await loadFacebookTokens();
        setCurrentStep(3);
        alert('Đã lưu Page Access Token thành công!');
      } else {
        alert('Lỗi lưu Page Access Token');
      }
    } catch (error) {
      alert('Lỗi lưu Page Access Token: ' + error.message);
    } finally {
      setLoading(false);
    }
  };

  // Step 3: Start Bot
  const handleStartBot = async () => {
    setLoading(true);
    try {
      const success = await facebookPageApi.facebook.startBot(shopId);
      if (success) {
        await loadBotStatus();
        setCurrentStep(4);
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

  // Stop Bot
  const handleStopBot = async () => {
    setLoading(true);
    try {
      const success = await facebookPageApi.facebook.stopBot(shopId);
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

  // Token Management
  const handleUpdateTokenStatus = async (tokenId, status) => {
    try {
      await facebookPageApi.tokens.updateTokenStatus(tokenId, status);
      await loadFacebookTokens();
      alert(`Đã cập nhật trạng thái token thành ${status}`);
    } catch (error) {
      alert('Lỗi cập nhật trạng thái token: ' + error.message);
    }
  };

  const handleDeleteToken = async (tokenId) => {
    if (!confirm('Bạn có chắc chắn muốn xóa token này?')) return;
    
    try {
      await facebookPageApi.tokens.deleteToken(tokenId);
      await loadFacebookTokens();
      alert('Đã xóa token thành công');
    } catch (error) {
      alert('Lỗi xóa token: ' + error.message);
    }
  };

  return (
    <div className="facebook-page-manager">
      <h2>Quản lý Facebook Page</h2>
      
      {/* Current Status */}
      {botStatus && (
        <div className="current-status">
          <h3>Trạng thái hiện tại</h3>
          <div className={`status-card ${botStatus.active ? 'active' : 'inactive'}`}>
            <div className="status-info">
              <p><strong>Shop ID:</strong> {botStatus.shopId}</p>
              <p><strong>Trạng thái:</strong> 
                <span className={`status-badge ${botStatus.active ? 'active' : 'inactive'}`}>
                  {botStatus.active ? 'Đang hoạt động' : 'Đã dừng'}
                </span>
              </p>
              <p><strong>Webhook URL:</strong></p>
              <code className="webhook-url">{botStatus.webhookUrl}</code>
            </div>
            <div className="status-actions">
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
          </div>
        </div>
      )}

      {/* Step-by-step Configuration */}
      <div className="configuration-steps">
        <h3>Cấu hình Facebook Page</h3>
        
        {/* Step 1: Webhook Configuration */}
        {currentStep >= 1 && (
          <div className="step">
            <div className="step-header">
              <span className="step-number">1</span>
              <h4>Cấu hình Webhook</h4>
            </div>
            
            {!webhookConfig ? (
              <div className="step-content">
                <p>Tạo webhook URL để nhận tin nhắn từ Facebook Messenger.</p>
                <button 
                  onClick={handleConfigureWebhook} 
                  disabled={loading}
                  className="btn btn-primary"
                >
                  {loading ? 'Đang cấu hình...' : 'Cấu hình Webhook'}
                </button>
              </div>
            ) : (
              <div className="step-content completed">
                <div className="webhook-info">
                  <p><strong>Webhook URL:</strong></p>
                  <code>{webhookConfig.webhookUrl}</code>
                  
                  <p><strong>Verify Token:</strong></p>
                  <code>{webhookConfig.verifyToken}</code>
                  
                  <div className="alert alert-info">
                    <p><strong>Hướng dẫn:</strong></p>
                    <ol>
                      <li>Truy cập <a href="https://developers.facebook.com/" target="_blank">Facebook Developers Console</a></li>
                      <li>Vào Messenger → Settings → Webhooks</li>
                      <li>Nhập Webhook URL và Verify Token ở trên</li>
                      <li>Subscribe to: messages, messaging_postbacks</li>
                      <li>Verify và Save</li>
                    </ol>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}

        {/* Step 2: Page Access Token */}
        {currentStep >= 2 && (
          <div className="step">
            <div className="step-header">
              <span className="step-number">2</span>
              <h4>Thêm Page Access Token</h4>
            </div>
            
            <div className="step-content">
              <div className="form-group">
                <label>Page Name (tùy chọn):</label>
                <input
                  type="text"
                  value={pageName}
                  onChange={(e) => setPageName(e.target.value)}
                  placeholder="Tên Page Facebook"
                  className="form-control"
                />
              </div>
              
              <div className="form-group">
                <label>Page ID: <span className="required">*</span></label>
                <input
                  type="text"
                  value={pageId}
                  onChange={(e) => setPageId(e.target.value)}
                  placeholder="Nhập Page ID từ Facebook"
                  className="form-control"
                  required
                />
              </div>
              
              <div className="form-group">
                <label>Page Access Token: <span className="required">*</span></label>
                <textarea
                  value={pageAccessToken}
                  onChange={(e) => setPageAccessToken(e.target.value)}
                  placeholder="Nhập Page Access Token từ Facebook"
                  className="form-control token-input"
                  rows="3"
                  required
                />
              </div>
              
              <button 
                onClick={handleSavePageToken} 
                disabled={loading || !pageAccessToken || !pageId}
                className="btn btn-primary"
              >
                {loading ? 'Đang lưu...' : 'Lưu Page Token'}
              </button>
              
              <div className="alert alert-info">
                <p><strong>Cách lấy Page Access Token:</strong></p>
                <ol>
                  <li>Vào <a href="https://developers.facebook.com/" target="_blank">Facebook Developers Console</a></li>
                  <li>Chọn App của bạn</li>
                  <li>Vào Messenger → Settings → Access Tokens</li>
                  <li>Chọn Page và Generate Token</li>
                  <li>Copy Page ID và Access Token</li>
                </ol>
              </div>
            </div>
          </div>
        )}

        {/* Step 3: Start Bot */}
        {currentStep >= 3 && (
          <div className="step">
            <div className="step-header">
              <span className="step-number">3</span>
              <h4>Khởi động Bot</h4>
            </div>
            
            <div className="step-content">
              <p>Khởi động bot để bắt đầu nhận và xử lý tin nhắn từ Facebook Messenger.</p>
              <button 
                onClick={handleStartBot} 
                disabled={loading}
                className="btn btn-success"
              >
                {loading ? 'Đang khởi động...' : 'Khởi động Bot Facebook'}
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Token Management */}
      {facebookTokens.length > 0 && (
        <div className="token-management">
          <h3>Quản lý Access Tokens</h3>
          <div className="tokens-list">
            {facebookTokens.map(token => (
              <div key={token.id} className="token-card">
                <div className="token-info">
                  <p><strong>Token ID:</strong> {token.id}</p>
                  <p><strong>Status:</strong> 
                    <span className={`status-badge ${token.status.toLowerCase()}`}>
                      {token.status}
                    </span>
                  </p>
                  <p><strong>Method:</strong> {token.method}</p>
                  <p><strong>Token:</strong></p>
                  <code className="token-preview">
                    {token.accessToken.substring(0, 20)}...
                  </code>
                </div>
                <div className="token-actions">
                  {token.status === 'ACTIVE' ? (
                    <button 
                      onClick={() => handleUpdateTokenStatus(token.id, 'EXPIRED')}
                      className="btn btn-warning btn-sm"
                    >
                      Vô hiệu hóa
                    </button>
                  ) : (
                    <button 
                      onClick={() => handleUpdateTokenStatus(token.id, 'ACTIVE')}
                      className="btn btn-success btn-sm"
                    >
                      Kích hoạt
                    </button>
                  )}
                  <button 
                    onClick={() => handleDeleteToken(token.id)}
                    className="btn btn-danger btn-sm"
                  >
                    Xóa
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Help Section */}
      <div className="help-section">
        <h3>Hỗ trợ</h3>
        <div className="help-content">
          <h4>Lỗi thường gặp:</h4>
          <ul>
            <li><strong>Webhook verification failed:</strong> Kiểm tra verify token có đúng không</li>
            <li><strong>Access token invalid:</strong> Token có thể đã hết hạn, tạo lại token mới</li>
            <li><strong>Bot not responding:</strong> Kiểm tra webhook subscription trong Facebook App</li>
            <li><strong>Permission denied:</strong> Đảm bảo Page permissions được cấp đúng</li>
          </ul>
          
          <h4>Debug steps:</h4>
          <ol>
            <li>Kiểm tra logs trong browser console</li>
            <li>Verify webhook URL accessible từ Facebook</li>
            <li>Test API endpoints với Postman</li>
            <li>Kiểm tra database cho configuration records</li>
          </ol>
        </div>
      </div>
    </div>
  );
};

export default FacebookPageManager;
```

#### FacebookPageManager.css
```css
/* FacebookPageManager.css */
.facebook-page-manager {
  max-width: 1000px;
  margin: 0 auto;
  padding: 20px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', sans-serif;
}

.facebook-page-manager h2 {
  color: #1877f2;
  border-bottom: 2px solid #1877f2;
  padding-bottom: 10px;
  margin-bottom: 30px;
}

/* Current Status */
.current-status {
  margin-bottom: 30px;
}

.status-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 20px;
  border: 2px solid #ddd;
}

.status-card.active {
  background-color: #d4edda;
  border-color: #28a745;
}

.status-card.inactive {
  background-color: #f8d7da;
  border-color: #dc3545;
}

.status-info p {
  margin: 8px 0;
}

.status-badge {
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: bold;
  margin-left: 8px;
}

.status-badge.active {
  background-color: #28a745;
  color: white;
}

.status-badge.inactive {
  background-color: #dc3545;
  color: white;
}

.webhook-url {
  display: block;
  background: #f4f4f4;
  padding: 8px;
  border-radius: 4px;
  margin-top: 5px;
  word-break: break-all;
  font-size: 12px;
}

/* Configuration Steps */
.configuration-steps {
  margin-bottom: 40px;
}

.step {
  margin-bottom: 25px;
  border: 1px solid #ddd;
  border-radius: 8px;
  overflow: hidden;
}

.step-header {
  background-color: #f8f9fa;
  padding: 15px 20px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid #ddd;
}

.step-number {
  background-color: #1877f2;
  color: white;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  margin-right: 15px;
}

.step-content {
  padding: 20px;
}

.step-content.completed {
  background-color: #f8f9fa;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: 600;
  color: #333;
}

.required {
  color: #dc3545;
}

.form-control {
  width: 100%;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 14px;
  transition: border-color 0.3s;
}

.form-control:focus {
  outline: none;
  border-color: #1877f2;
  box-shadow: 0 0 0 2px rgba(24, 119, 242, 0.2);
}

.token-input {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  resize: vertical;
}

/* Buttons */
.btn {
  padding: 12px 20px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  display: inline-block;
  transition: all 0.3s;
  margin-right: 10px;
}

.btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 8px rgba(0,0,0,0.1);
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.btn-primary {
  background-color: #1877f2;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background-color: #166fe5;
}

.btn-success {
  background-color: #28a745;
  color: white;
}

.btn-success:hover:not(:disabled) {
  background-color: #218838;
}

.btn-danger {
  background-color: #dc3545;
  color: white;
}

.btn-danger:hover:not(:disabled) {
  background-color: #c82333;
}

.btn-warning {
  background-color: #ffc107;
  color: #212529;
}

.btn-warning:hover:not(:disabled) {
  background-color: #e0a800;
}

.btn-sm {
  padding: 6px 12px;
  font-size: 12px;
}

/* Alerts */
.alert {
  padding: 15px;
  margin: 15px 0;
  border: 1px solid transparent;
  border-radius: 6px;
}

.alert-info {
  background-color: #d1ecf1;
  border-color: #bee5eb;
  color: #0c5460;
}

.alert ol, .alert ul {
  margin: 10px 0 0 20px;
}

.alert a {
  color: #0c5460;
  font-weight: 600;
}

/* Token Management */
.token-management {
  margin-bottom: 30px;
}

.tokens-list {
  display: grid;
  gap: 15px;
}

.token-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px;
  border: 1px solid #ddd;
  border-radius: 6px;
  background-color: #f8f9fa;
}

.token-info p {
  margin: 5px 0;
  font-size: 14px;
}

.token-preview {
  background: #e9ecef;
  padding: 4px 8px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 11px;
}

.token-actions {
  display: flex;
  gap: 8px;
}

/* Help Section */
.help-section {
  background-color: #f8f9fa;
  padding: 20px;
  border-radius: 8px;
  border-left: 4px solid #1877f2;
}

.help-content h4 {
  color: #1877f2;
  margin-top: 20px;
  margin-bottom: 10px;
}

.help-content h4:first-child {
  margin-top: 0;
}

.help-content ul, .help-content ol {
  margin-left: 20px;
}

.help-content li {
  margin-bottom: 8px;
}

.help-content strong {
  color: #333;
}

/* Responsive */
@media (max-width: 768px) {
  .facebook-page-manager {
    padding: 15px;
  }
  
  .status-card {
    flex-direction: column;
    gap: 15px;
    text-align: center;
  }
  
  .token-card {
    flex-direction: column;
    gap: 15px;
    text-align: center;
  }
  
  .btn {
    margin: 5px;
  }
}

/* Code blocks */
code {
  background-color: #f4f4f4;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
  font-size: 13px;
}

/* Status badges in token list */
.status-badge.active {
  background-color: #28a745;
}

.status-badge.expired {
  background-color: #ffc107;
  color: #212529;
}

.status-badge.revoked {
  background-color: #dc3545;
}
```

### 3. Integration với Shop Management

#### ShopManagement.jsx (Updated)
```javascript
// ShopManagement.jsx
import React, { useState } from 'react';
import FacebookPageManager from './components/FacebookPageManager';
import TelegramBotManager from './components/TelegramBotManager';

const ShopManagement = ({ shopId }) => {
  const [activeTab, setActiveTab] = useState('overview');

  return (
    <div className="shop-management">
      <h1>Quản lý Shop #{shopId}</h1>
      
      <div className="tabs">
        <button 
          className={`tab ${activeTab === 'overview' ? 'active' : ''}`}
          onClick={() => setActiveTab('overview')}
        >
          Tổng quan
        </button>
        <button 
          className={`tab ${activeTab === 'facebook' ? 'active' : ''}`}
          onClick={() => setActiveTab('facebook')}
        >
          Facebook Messenger
        </button>
        <button 
          className={`tab ${activeTab === 'telegram' ? 'active' : ''}`}
          onClick={() => setActiveTab('telegram')}
        >
          Telegram Bot
        </button>
      </div>

      <div className="tab-content">
        {activeTab === 'overview' && (
          <div>
            <h2>Tổng quan Shop</h2>
            <p>Thông tin chung về shop và các tích hợp...</p>
          </div>
        )}
        
        {activeTab === 'facebook' && (
          <FacebookPageManager shopId={shopId} />
        )}
        
        {activeTab === 'telegram' && (
          <TelegramBotManager shopId={shopId} />
        )}
      </div>
    </div>
  );
};

export default ShopManagement;
```

## Luồng sử dụng

### 1. Cho Shop Owner:
1. **Truy cập Shop Management** → Chọn tab "Facebook Messenger"
2. **Cấu hình Webhook** → Nhấn "Cấu hình Webhook" → Copy URL và Verify Token
3. **Thiết lập Facebook App** → Vào Facebook Developers Console → Cấu hình webhook
4. **Thêm Page Access Token** → Lấy token từ Facebook → Nhập vào hệ thống  
5. **Khởi động Bot** → Nhấn "Khởi động Bot" → Bot bắt đầu hoạt động
6. **Quản lý Tokens** → Xem, cập nhật, xóa các tokens khi cần

### 2. Cho Customer:
1. **Tìm Facebook Page** của shop
2. **Gửi tin nhắn** → AI bot sẽ phản hồi tự động
3. **Đặt hàng** thông qua chat
4. **Cập nhật địa chỉ** bằng lệnh `/address`

## Best Practices

### 1. Security
- Luôn validate tokens trước khi lưu
- Encrypt sensitive data trong database
- Implement rate limiting cho API calls
- Log tất cả các actions để audit

### 2. Error Handling
- Implement retry logic cho Facebook API calls
- Graceful degradation khi service không khả dụng
- Clear error messages cho users
- Monitoring và alerting cho production

### 3. Performance
- Cache Facebook API responses khi có thể
- Optimize database queries
- Implement pagination cho danh sách tokens
- Use connection pooling

### 4. User Experience
- Progressive disclosure trong UI
- Loading states cho tất cả async operations
- Clear visual feedback cho user actions
- Responsive design cho mobile users

## Troubleshooting

### Common Issues:
1. **Token expiration** → Implement auto-refresh mechanism
2. **Webhook verification fails** → Check verify token matching
3. **Permission errors** → Ensure proper Facebook app permissions
4. **Rate limiting** → Implement exponential backoff

### Debug Tools:
1. Browser DevTools cho frontend debugging
2. Facebook Graph API Explorer cho testing
3. ngrok logs cho webhook debugging
4. Application logs cho backend issues

## Kết luận

Hướng dẫn này cung cấp một hệ thống hoàn chỉnh để quản lý Facebook Page integration với shop. Frontend components được thiết kế để dễ sử dụng và có thể mở rộng, while backend APIs cung cấp flexible management cho tokens và bot configuration.
