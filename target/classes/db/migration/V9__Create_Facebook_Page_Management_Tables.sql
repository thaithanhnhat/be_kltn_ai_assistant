-- Add Facebook Page Management table for better organization
-- This table will help manage multiple pages per shop more efficiently

CREATE TABLE IF NOT EXISTS facebook_page_configurations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    page_id VARCHAR(255) NOT NULL UNIQUE,
    page_name VARCHAR(255),
    access_token TEXT NOT NULL,
    verify_token VARCHAR(255) NOT NULL,
    webhook_url VARCHAR(500),
    subscribed_events TEXT DEFAULT 'messages,messaging_postbacks,message_deliveries',
    is_active BOOLEAN DEFAULT TRUE,
    is_webhook_verified BOOLEAN DEFAULT FALSE,
    last_webhook_challenge VARCHAR(255),
    webhook_verified_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Foreign key constraint
    CONSTRAINT fk_facebook_page_shop FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE,
    
    -- Indexes for better performance
    INDEX idx_facebook_page_shop_id (shop_id),
    INDEX idx_facebook_page_page_id (page_id),
    INDEX idx_facebook_page_active (is_active),
    INDEX idx_facebook_page_shop_active (shop_id, is_active),
    INDEX idx_facebook_page_webhook_verified (is_webhook_verified)
);

-- Add Facebook Message Log table for debugging and monitoring
CREATE TABLE IF NOT EXISTS facebook_message_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    page_id VARCHAR(255) NOT NULL,
    sender_id VARCHAR(255) NOT NULL,
    recipient_id VARCHAR(255) NOT NULL,
    message_text TEXT,
    message_type ENUM('incoming', 'outgoing') NOT NULL,
    ai_intent VARCHAR(100),
    ai_confidence DECIMAL(3,2),
    processing_time_ms INT,
    status ENUM('success', 'failed', 'pending') DEFAULT 'pending',
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Indexes
    INDEX idx_facebook_msg_shop_page (shop_id, page_id),
    INDEX idx_facebook_msg_sender (sender_id),
    INDEX idx_facebook_msg_created (created_at),
    INDEX idx_facebook_msg_status (status)
);

-- Add Facebook Webhook Events table for monitoring
CREATE TABLE IF NOT EXISTS facebook_webhook_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    page_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_data JSON,
    processed BOOLEAN DEFAULT FALSE,
    processing_error TEXT,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    
    -- Indexes
    INDEX idx_facebook_webhook_page (page_id),
    INDEX idx_facebook_webhook_type (event_type),
    INDEX idx_facebook_webhook_processed (processed),
    INDEX idx_facebook_webhook_received (received_at)
);
