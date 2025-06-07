# Hướng dẫn tích hợp Facebook Page cho Shop - Frontend Implementation

## Tổng quan
Hướng dẫn này mô tả cách tích hợp frontend để thêm và quản lý Facebook Page cho shop, bao gồm cả workflow hoàn chỉnh và UI components.

## Kiến trúc hệ thống

### 1. Database Schema
```sql
-- Facebook Access Tokens Table (Dedicated)
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

-- Generic Access Tokens Table
CREATE TABLE access_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    shop_id BIGINT NOT NULL,
    access_token VARCHAR(512) NOT NULL,
    status ENUM('ACTIVE', 'EXPIRED', 'REVOKED') NOT NULL,
    method ENUM('TELEGRAM', 'FACEBOOK') NOT NULL
);
```

### 2. API Endpoints

#### Facebook Bot Management
- `POST /api/facebook/shops/{shopId}/configure` - Cấu hình webhook
- `POST /api/facebook/shops/{shopId}/access-token` - Lưu Page Access Token
- `POST /api/facebook/shops/{shopId}/start` - Khởi động bot
- `POST /api/facebook/shops/{shopId}/stop` - Dừng bot
- `GET /api/facebook/shops/{shopId}/status` - Kiểm tra trạng thái bot

#### Generic Token Management
- `POST /api/integration/tokens` - Thêm access token
- `GET /api/integration/tokens/shop/{shopId}` - Lấy tokens của shop
- `GET /api/integration/tokens/shop/{shopId}/method/FACEBOOK` - Lấy Facebook tokens của shop
- `PATCH /api/integration/tokens/{id}/status` - Cập nhật trạng thái token
- `DELETE /api/integration/tokens/{id}` - Xóa token

## Frontend Implementation

### 1. API Integration Layer

#### facebookPageApi.js
```javascript
// facebookPageApi.js
const API_BASE = '/api';

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
      setPageAccessToken('');
      setPageId('');
    }
  };

  // Step 3: Start Bot
  const handleStartBot = async () => {
    setLoading(true);
    try {
      const success = await facebookPageApi.facebook.startBot(shopId);
      if (success) {
        setCurrentStep(4);
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
      alert('Đã xóa token thành công!');
    } catch (error) {
      alert('Lỗi xóa token: ' + error.message);
    }
  };

  return (
    <div className="facebook-page-manager">
      <h2>Facebook Page Manager</h2>
      
      {/* Progress Indicator */}
      <div className="progress-indicator">
        <div className={`step ${currentStep >= 1 ? 'active' : ''}`}>1. Webhook</div>
        <div className={`step ${currentStep >= 2 ? 'active' : ''}`}>2. Token</div>
        <div className={`step ${currentStep >= 3 ? 'active' : ''}`}>3. Start</div>
        <div className={`step ${currentStep >= 4 ? 'active' : ''}`}>4. Active</div>
      </div>

      {/* Step 1: Configure Webhook */}
      {currentStep >= 1 && (
        <div className="step">
          <div className="step-header">
            <span className="step-number">1</span>
            <h3>Cấu hình Webhook</h3>
          </div>
          
          {!webhookConfig ? (
            <div className="step-content">
              <p>Cấu hình webhook để nhận tin nhắn từ Facebook.</p>
              <button 
                onClick={handleConfigureWebhook} 
                disabled={loading}
                className="btn btn-primary"
              >
                {loading ? 'Đang cấu hình...' : 'Cấu hình Webhook'}
              </button>
            </div>
          ) : (
            <div className="step-content">
              <div className="webhook-info">
                <h4>Thông tin Webhook:</h4>
                <div className="info-item">
                  <label>Webhook URL:</label>
                  <code>{webhookConfig.webhookUrl}</code>
                  <button 
                    onClick={() => navigator.clipboard.writeText(webhookConfig.webhookUrl)}
                    className="btn btn-sm btn-secondary"
                  >
                    Copy
                  </button>
                </div>
                <div className="info-item">
                  <label>Verify Token:</label>
                  <code>{webhookConfig.verifyToken}</code>
                  <button 
                    onClick={() => navigator.clipboard.writeText(webhookConfig.verifyToken)}
                    className="btn btn-sm btn-secondary"
                  >
                    Copy
                  </button>
                </div>
                
                <div className="alert alert-info">
                  <p><strong>Cách cấu hình trong Facebook App:</strong></p>
                  <ol>
                    <li>Vào <a href="https://developers.facebook.com/" target="_blank">Facebook Developers Console</a></li>
                    <li>Chọn App của bạn → Messenger → Settings → Webhooks</li>
                    <li>Edit Callback URL và nhập thông tin trên</li>
                    <li>Subscribe to: messages, messaging_postbacks</li>
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
            <h3>Thêm Page Access Token</h3>
          </div>
          
          <div className="step-content">
            <div className="form-group">
              <label>Page Name (tùy chọn):</label>
              <input
                type="text"
                value={pageName}
                onChange={(e) => setPageName(e.target.value)}
                placeholder="Tên Facebook Page"
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
            <h3>Khởi động Bot</h3>
          </div>
          
          <div className="step-content">
            <p>Khởi động bot để bắt đầu nhận và trả lời tin nhắn từ Facebook.</p>
            <button 
              onClick={handleStartBot} 
              disabled={loading}
              className="btn btn-success"
            >
              {loading ? 'Đang khởi động...' : 'Khởi động Bot'}
            </button>
          </div>
        </div>
      )}

      {/* Step 4: Bot Status */}
      {currentStep >= 4 && botStatus && (
        <div className="step">
          <div className="step-header">
            <span className="step-number">4</span>
            <h3>Trạng thái Bot</h3>
          </div>
          
          <div className="step-content">
            <div className="status-info">
              <div className={`status-badge ${botStatus.active ? 'active' : 'inactive'}`}>
                {botStatus.active ? 'Đang hoạt động' : 'Không hoạt động'}
              </div>
              
              {botStatus.active && (
                <div className="bot-details">
                  <p><strong>Shop ID:</strong> {botStatus.shopId}</p>
                  <p><strong>Webhook URL:</strong> {botStatus.webhookUrl}</p>
                </div>
              )}
              
              <div className="bot-controls">
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
                    {loading ? 'Đang khởi động...' : 'Khởi động lại Bot'}
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Token Management Section */}
      {facebookTokens.length > 0 && (
        <div className="token-management">
          <h3>Quản lý Tokens</h3>
          <div className="tokens-list">
            {facebookTokens.map(token => (
              <div key={token.id} className="token-item">
                <div className="token-info">
                  <div className="token-id">Token #{token.id}</div>
                  <div className={`token-status ${token.status.toLowerCase()}`}>
                    {token.status}
                  </div>
                  <div className="token-method">{token.method}</div>
                </div>
                
                <div className="token-actions">
                  {token.status === 'ACTIVE' && (
                    <button 
                      onClick={() => handleUpdateTokenStatus(token.id, 'EXPIRED')}
                      className="btn btn-sm btn-warning"
                    >
                      Deactivate
                    </button>
                  )}
                  
                  {token.status === 'EXPIRED' && (
                    <button 
                      onClick={() => handleUpdateTokenStatus(token.id, 'ACTIVE')}
                      className="btn btn-sm btn-success"
                    >
                      Activate
                    </button>
                  )}
                  
                  <button 
                    onClick={() => handleDeleteToken(token.id)}
                    className="btn btn-sm btn-danger"
                  >
                    Delete
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Troubleshooting Section */}
      <div className="troubleshooting">
        <h3>Troubleshooting</h3>
        <div className="help-content">
          <h4>Các vấn đề thường gặp:</h4>
          <ul>
            <li><strong>Webhook verification failed:</strong> Kiểm tra URL và verify token</li>
            <li><strong>Token invalid:</strong> Tạo lại Page Access Token từ Facebook</li>
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
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', sans-serif;
}

.facebook-page-manager h2 {
  color: #1877f2;
  text-align: center;
  margin-bottom: 30px;
}

/* Progress Indicator */
.progress-indicator {
  display: flex;
  justify-content: center;
  margin-bottom: 30px;
  padding: 20px 0;
  border-bottom: 2px solid #e4e6ea;
}

.progress-indicator .step {
  padding: 8px 16px;
  margin: 0 10px;
  border-radius: 20px;
  background-color: #f0f2f5;
  color: #65676b;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.3s ease;
}

.progress-indicator .step.active {
  background-color: #1877f2;
  color: white;
}

/* Steps */
.step {
  margin-bottom: 30px;
  border: 1px solid #e4e6ea;
  border-radius: 12px;
  background-color: white;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.step-header {
  display: flex;
  align-items: center;
  padding: 20px;
  background-color: #f8f9fa;
  border-bottom: 1px solid #e4e6ea;
  border-radius: 12px 12px 0 0;
}

.step-number {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  background-color: #1877f2;
  color: white;
  border-radius: 50%;
  font-weight: bold;
  margin-right: 15px;
}

.step-header h3 {
  margin: 0;
  color: #1c1e21;
}

.step-content {
  padding: 20px;
}

/* Form Elements */
.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: 600;
  color: #1c1e21;
}

.required {
  color: #e41e3f;
}

.form-control {
  width: 100%;
  padding: 12px;
  border: 1px solid #dddfe2;
  border-radius: 8px;
  font-size: 16px;
  transition: border-color 0.3s ease;
}

.form-control:focus {
  outline: none;
  border-color: #1877f2;
  box-shadow: 0 0 0 2px rgba(24, 119, 242, 0.2);
}

.token-input {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 14px;
  resize: vertical;
}

/* Buttons */
.btn {
  padding: 12px 24px;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-right: 10px;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-primary {
  background-color: #1877f2;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background-color: #166fe5;
}

.btn-success {
  background-color: #42b883;
  color: white;
}

.btn-success:hover:not(:disabled) {
  background-color: #369870;
}

.btn-danger {
  background-color: #e41e3f;
  color: white;
}

.btn-danger:hover:not(:disabled) {
  background-color: #c71e37;
}

.btn-warning {
  background-color: #ff9500;
  color: white;
}

.btn-warning:hover:not(:disabled) {
  background-color: #e6860a;
}

.btn-secondary {
  background-color: #6c757d;
  color: white;
}

.btn-secondary:hover:not(:disabled) {
  background-color: #5a6268;
}

.btn-sm {
  padding: 6px 12px;
  font-size: 14px;
}

/* Info boxes */
.webhook-info {
  background-color: #f0f2f5;
  padding: 20px;
  border-radius: 8px;
  margin-top: 15px;
}

.info-item {
  margin-bottom: 15px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.info-item label {
  font-weight: 600;
  margin-bottom: 0;
  min-width: 120px;
}

.info-item code {
  flex: 1;
  background: #ffffff;
  padding: 8px 12px;
  border-radius: 4px;
  border: 1px solid #dddfe2;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 14px;
  word-break: break-all;
}

/* Alerts */
.alert {
  padding: 16px;
  border-radius: 8px;
  margin-top: 20px;
}

.alert-info {
  background-color: #d1ecf1;
  border: 1px solid #bee5eb;
  color: #0c5460;
}

.alert-info a {
  color: #0c5460;
  font-weight: 600;
}

/* Status */
.status-info {
  text-align: center;
}

.status-badge {
  display: inline-block;
  padding: 8px 16px;
  border-radius: 20px;
  font-weight: 600;
  margin-bottom: 20px;
}

.status-badge.active {
  background-color: #d4edda;
  color: #155724;
}

.status-badge.inactive {
  background-color: #f8d7da;
  color: #721c24;
}

.bot-details {
  background-color: #f8f9fa;
  padding: 15px;
  border-radius: 8px;
  margin: 20px 0;
  text-align: left;
}

.bot-controls {
  margin-top: 20px;
}

/* Token Management */
.token-management {
  margin-top: 40px;
  padding-top: 30px;
  border-top: 2px solid #e4e6ea;
}

.tokens-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.token-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px;
  border: 1px solid #e4e6ea;
  border-radius: 8px;
  background-color: #f8f9fa;
}

.token-info {
  display: flex;
  align-items: center;
  gap: 20px;
}

.token-id {
  font-weight: 600;
  color: #1c1e21;
}

.token-status {
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
}

.token-status.active {
  background-color: #d4edda;
  color: #155724;
}

.token-status.expired {
  background-color: #fff3cd;
  color: #856404;
}

.token-status.revoked {
  background-color: #f8d7da;
  color: #721c24;
}

.token-method {
  padding: 4px 8px;
  background-color: #1877f2;
  color: white;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.token-actions {
  display: flex;
  gap: 10px;
}

/* Troubleshooting */
.troubleshooting {
  margin-top: 40px;
  padding: 20px;
  background-color: #f8f9fa;
  border-radius: 8px;
}

.troubleshooting h3 {
  color: #1c1e21;
  margin-bottom: 15px;
}

.help-content h4 {
  color: #65676b;
  margin-top: 20px;
  margin-bottom: 10px;
}

.help-content ul,
.help-content ol {
  color: #65676b;
  padding-left: 20px;
}

.help-content li {
  margin-bottom: 8px;
}

/* Responsive */
@media (max-width: 768px) {
  .facebook-page-manager {
    padding: 15px;
  }
  
  .progress-indicator {
    flex-wrap: wrap;
  }
  
  .progress-indicator .step {
    margin: 5px;
    font-size: 12px;
    padding: 6px 12px;
  }
  
  .step-header {
    padding: 15px;
  }
  
  .step-content {
    padding: 15px;
  }
  
  .info-item {
    flex-direction: column;
    align-items: stretch;
  }
  
  .info-item label {
    min-width: auto;
  }
  
  .token-item {
    flex-direction: column;
    gap: 15px;
  }
  
  .token-info {
    justify-content: center;
  }
  
  .token-actions {
    justify-content: center;
  }
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

### 1. Security:
- Store access tokens encrypted
- Use HTTPS cho tất cả webhook endpoints
- Validate webhook signatures từ Facebook
- Implement rate limiting

### 2. Error Handling:
- Graceful error handling cho tất cả API calls
- User-friendly error messages
- Retry mechanisms cho failed requests
- Logging cho debugging

### 3. Performance:
- Cache bot status locally
- Debounce user inputs
- Lazy load components
- Optimize API calls

### 4. User Experience:
- Clear step-by-step workflow
- Progress indicators
- Copy-to-clipboard functionality
- Responsive design
- Help documentation

## Troubleshooting

### Common Issues:
1. **Webhook verification failed** → Check URL accessibility
2. **Invalid access token** → Regenerate from Facebook
3. **Bot not responding** → Check webhook subscriptions
4. **Permission errors** → Verify Page permissions

### Debug Tools:
1. Browser Developer Console
2. Facebook Webhook Tester
3. API testing với Postman
4. Database queries để check configuration

## Kết luận

Hướng dẫn này cung cấp:
- Complete frontend implementation cho Facebook Page integration
- Step-by-step user workflow với clear UI
- Comprehensive error handling và debugging
- Token management với unified API
- Responsive design và good UX practices
- Production-ready code với security considerations

Frontend developers có thể sử dụng hướng dẫn này để implement hoàn chỉnh Facebook Page integration cho shop management system.
