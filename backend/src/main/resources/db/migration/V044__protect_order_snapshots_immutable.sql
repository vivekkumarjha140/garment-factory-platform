-- Same pattern as V028. Frozen prices and the status trail can never be edited or removed.
CREATE TRIGGER trg_order_items_no_update
    BEFORE UPDATE ON order_items
    FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'order_items is immutable: UPDATE not allowed';

CREATE TRIGGER trg_order_items_no_delete
    BEFORE DELETE ON order_items
    FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'order_items is immutable: DELETE not allowed';

CREATE TRIGGER trg_order_status_history_no_update
    BEFORE UPDATE ON order_status_history
    FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'order_status_history is immutable: UPDATE not allowed';

CREATE TRIGGER trg_order_status_history_no_delete
    BEFORE DELETE ON order_status_history
    FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'order_status_history is immutable: DELETE not allowed';
