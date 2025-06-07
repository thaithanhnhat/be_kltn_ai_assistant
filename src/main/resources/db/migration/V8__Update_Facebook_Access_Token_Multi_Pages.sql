-- Update Facebook Access Token table to support multiple pages per shop
-- Add new columns for better Facebook integration

-- Add new columns if they don't exist
ALTER TABLE facebook_access_tokens 
ADD COLUMN IF NOT EXISTS page_name VARCHAR(255),
ADD COLUMN IF NOT EXISTS subscribed_events TEXT;

-- Make page_id required
ALTER TABLE facebook_access_tokens 
MODIFY COLUMN page_id VARCHAR(255) NOT NULL;

-- Make verify_token required  
ALTER TABLE facebook_access_tokens 
MODIFY COLUMN verify_token VARCHAR(255) NOT NULL;

-- Add unique constraint on page_id to prevent duplicate page configurations
ALTER TABLE facebook_access_tokens 
ADD CONSTRAINT uk_facebook_page_id UNIQUE (page_id);

-- Add index for better performance on shop_id queries
CREATE INDEX IF NOT EXISTS idx_facebook_tokens_shop_id ON facebook_access_tokens(shop_id);

-- Add index for better performance on page_id queries
CREATE INDEX IF NOT EXISTS idx_facebook_tokens_page_id ON facebook_access_tokens(page_id);

-- Add index for active status queries
CREATE INDEX IF NOT EXISTS idx_facebook_tokens_active ON facebook_access_tokens(is_active);

-- Add composite index for shop_id and active status
CREATE INDEX IF NOT EXISTS idx_facebook_tokens_shop_active ON facebook_access_tokens(shop_id, is_active);

-- Add composite index for page_id and active status
CREATE INDEX IF NOT EXISTS idx_facebook_tokens_page_active ON facebook_access_tokens(page_id, is_active);
