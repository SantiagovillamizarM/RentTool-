CREATE TABLE users (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(120) NOT NULL, email VARCHAR(190) NOT NULL UNIQUE, password_hash VARCHAR(255) NOT NULL, role VARCHAR(30) NOT NULL, active BOOLEAN NOT NULL);
CREATE TABLE categories (id BIGINT PRIMARY KEY, name VARCHAR(120) NOT NULL UNIQUE, icon VARCHAR(20) NOT NULL);
CREATE TABLE tools (id BIGINT AUTO_INCREMENT PRIMARY KEY, provider_id BIGINT NOT NULL, category_id BIGINT NOT NULL, name VARCHAR(160) NOT NULL, description VARCHAR(3000) NOT NULL, daily_rate DECIMAL(14,2) NOT NULL CHECK(daily_rate>0), active BOOLEAN NOT NULL, FOREIGN KEY(provider_id) REFERENCES users(id), FOREIGN KEY(category_id) REFERENCES categories(id));
CREATE INDEX idx_tools_owner ON tools(provider_id,active);
CREATE INDEX idx_tools_category ON tools(category_id,active);
CREATE TABLE units (id BIGINT AUTO_INCREMENT PRIMARY KEY, tool_id BIGINT NOT NULL, code VARCHAR(80) NOT NULL, status VARCHAR(30) NOT NULL CHECK(status IN ('OPERATIVA','MANTENIMIENTO','RETIRADA')), UNIQUE(tool_id,code), FOREIGN KEY(tool_id) REFERENCES tools(id));
CREATE TABLE tool_images (id BIGINT AUTO_INCREMENT PRIMARY KEY, tool_id BIGINT NOT NULL, location VARCHAR(1000) NOT NULL, FOREIGN KEY(tool_id) REFERENCES tools(id));
INSERT INTO categories(id,name,icon) VALUES (1,'Herramientas eléctricas','↯'),(2,'Construcción y demolición','▨'),(3,'Medición y precisión','⌖'),(4,'Trabajo en altura','↗');
