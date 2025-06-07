-- Create Facebook monitoring and analytics tables

-- Facebook message logs table
CREATE TABLE IF NOT EXISTS facebook_message_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    page_id VARCHAR(255) NOT NULL,
    sender_id VARCHAR(255) NOT NULL,
    recipient_id VARCHAR(255) NOT NULL,
    message_text TEXT,
    message_type ENUM('incoming', 'outgoing') NOT NULL,
    ai_intent VARCHAR(255),
    ai_confidence DECIMAL(5,4),
    processing_time_ms BIGINT,
    status VARCHAR(50) DEFAULT 'success',
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_shop_page (shop_id, page_id),
    INDEX idx_sender (sender_id),
    INDEX idx_created_at (created_at),
    INDEX idx_message_type (message_type),
    INDEX idx_ai_intent (ai_intent)
);

-- Facebook webhook events table
CREATE TABLE IF NOT EXISTS facebook_webhook_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    page_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_data JSON,
    processing_status VARCHAR(50) DEFAULT 'pending',
    processing_time_ms BIGINT,
    error_message TEXT,
    retry_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    
    INDEX idx_page_id (page_id),
    INDEX idx_event_type (event_type),
    INDEX idx_processing_status (processing_status),
    INDEX idx_created_at (created_at)
);

-- Facebook page metrics table (daily aggregates)
CREATE TABLE IF NOT EXISTS facebook_page_metrics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    page_id VARCHAR(255) NOT NULL,
    metric_date DATE NOT NULL,
    messages_received INT DEFAULT 0,
    messages_sent INT DEFAULT 0,
    unique_users INT DEFAULT 0,
    orders_created INT DEFAULT 0,
    avg_response_time_ms BIGINT DEFAULT 0,
    webhook_events_processed INT DEFAULT 0,
    webhook_errors INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_shop_page_date (shop_id, page_id, metric_date),
    INDEX idx_shop_id (shop_id),
    INDEX idx_page_id (page_id),
    INDEX idx_metric_date (metric_date)
);

-- Facebook webhook health status table
CREATE TABLE IF NOT EXISTS facebook_webhook_health (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    page_id VARCHAR(255) NOT NULL UNIQUE,
    last_webhook_received TIMESTAMP NULL,
    last_successful_response TIMESTAMP NULL,
    consecutive_failures INT DEFAULT 0,
    total_requests_today INT DEFAULT 0,
    total_errors_today INT DEFAULT 0,
    avg_response_time_today_ms BIGINT DEFAULT 0,
    status ENUM('healthy', 'warning', 'critical', 'unknown') DEFAULT 'unknown',
    last_status_check TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_status (status),
    INDEX idx_last_webhook_received (last_webhook_received)
);
