-- Add balance_updated column to blockchain_transactions table
ALTER TABLE blockchain_transactions
ADD COLUMN balance_updated BOOLEAN DEFAULT FALSE NOT NULL; 