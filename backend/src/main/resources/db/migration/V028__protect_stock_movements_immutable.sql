CREATE TRIGGER trg_stock_movements_no_update
    BEFORE UPDATE ON stock_movements
    FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'stock_movements is immutable: UPDATE not allowed';

CREATE TRIGGER trg_stock_movements_no_delete
    BEFORE DELETE ON stock_movements
    FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'stock_movements is immutable: DELETE not allowed';