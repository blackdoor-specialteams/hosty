CREATE TABLE users (
	email      VARCHAR PRIMARY KEY,
	password	bytea   NOT NULL,
	salt        bytea   NOT NULL
);

CREATE TABLE services (
	name    VARCHAR PRIMARY KEY,
	owner   VARCHAR FOREIGN KEY REFERENCES users.email,
);