-- Where the rendered PDF lives in file storage. (pdf_file_id stays unused: there is no files table.)
ALTER TABLE invoices ADD COLUMN pdf_storage_key VARCHAR(255) NULL AFTER pdf_file_id;
