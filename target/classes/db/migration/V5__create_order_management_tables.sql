-- Create customers table
CREATE TABLE customers (
    id_customer BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_shop BIGINT NOT NULL,
    fullname VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    email VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_shop) REFERENCES shops(id),
    UNIQUE KEY uk_customer_email_shop (email, id_shop),
    UNIQUE KEY uk_customer_phone_shop (phone, id_shop)
);

-- Create orders table
CREATE TABLE orders (
    id_order BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_customer BIGINT NOT NULL,
    id_product BIGINT NOT NULL,
    note TEXT,
    quantity INT NOT NULL,
    delivery_unit VARCHAR(255),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_customer) REFERENCES customers(id_customer),
    FOREIGN KEY (id_product) REFERENCES products(id)
);

-- Create feedbacks table
CREATE TABLE feedbacks (
    id_feedback BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_customer BIGINT NOT NULL,
    id_product BIGINT NOT NULL,
    content TEXT NOT NULL,
    time TIMESTAMP NOT NULL,
    FOREIGN KEY (id_customer) REFERENCES customers(id_customer),
    FOREIGN KEY (id_product) REFERENCES products(id)
); 