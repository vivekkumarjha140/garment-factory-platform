package com.garmentfactory.cart.service;

import com.garmentfactory.cart.entity.Cart;
import com.garmentfactory.cart.entity.CartItem;
import com.garmentfactory.cart.entity.DiscountType;
import com.garmentfactory.cart.repository.CartItemRepository;
import com.garmentfactory.cart.repository.CartRepository;
import com.garmentfactory.cart.service.CartView.LineIssue;
import com.garmentfactory.cart.service.VariantPricer.PricedVariant;
import com.garmentfactory.inventory.api.InventoryApi;
import com.garmentfactory.inventory.api.StockAvailability;
import com.garmentfactory.shipping.api.ShippingQuote;
import com.garmentfactory.shipping.api.ShippingQuoteApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartServiceTest {

    private static final long USER = 7L;

    private CartRepository carts;
    private CartItemRepository items;
    private VariantPricer pricer;
    private InventoryApi inventory;
    private CouponEvaluator coupons;
    private ShippingQuoteApi shipping;
    private CartService service;

    @BeforeEach
    void setUp() {
        carts = Mockito.mock(CartRepository.class);
        items = Mockito.mock(CartItemRepository.class);
        pricer = Mockito.mock(VariantPricer.class);
        inventory = Mockito.mock(InventoryApi.class);
        coupons = Mockito.mock(CouponEvaluator.class);
        shipping = Mockito.mock(ShippingQuoteApi.class);
        service = new CartService(carts, items, pricer, inventory, coupons,
                new TaxCalculator(), shipping, 1000, 50);
    }

    // ---- helpers

    private Cart cart(String couponCode) {
        Cart c = new Cart(USER);
        ReflectionTestUtils.setField(c, "id", 1L);
        c.setCouponCode(couponCode);
        when(carts.lockByUserId(USER)).thenReturn(Optional.of(c));
        when(carts.findByUserId(USER)).thenReturn(Optional.of(c));
        return c;
    }

    private CartItem item(long id, long variantId, int qty) {
        CartItem i = new CartItem(1L, variantId, null, qty);
        ReflectionTestUtils.setField(i, "id", id);
        return i;
    }

    /** One variant (public id "pub-<id>"): price, GST %, weight (g/unit), sellable flag and available stock. */
    private void variant(long variantId, String price, String gstRate, int weight, boolean sellable, int available) {
        PricedVariant p = new PricedVariant(variantId, "pub-" + variantId, "Cotton Tee", "cotton-tee",
                "TEE-" + variantId, "M", "Black", new BigDecimal(price), new BigDecimal(gstRate), weight, sellable);
        when(pricer.resolveVariantId("pub-" + variantId)).thenReturn(Optional.of(variantId));
        when(pricer.priceLines(anyList())).thenReturn(Map.of(variantId, p));
        StockAvailability s = new StockAvailability(variantId, available, 0, available);
        when(inventory.availability(variantId)).thenReturn(Optional.of(s));
        when(inventory.availability(Mockito.<Collection<Long>>any())).thenReturn(Map.of(variantId, s));
    }

    // ---- tests

    @Test
    void emptyViewWhenUserHasNoCart() {
        when(carts.findByUserId(USER)).thenReturn(Optional.empty());

        CartView v = service.getCart(USER, null);

        assertThat(v.lines()).isEmpty();
        assertThat(v.totals().total()).isEqualByComparingTo("0.00");
        assertThat(v.checkoutReady()).isFalse();
    }

    @Test
    void addItemCreatesNewLineAndPricesIt() {
        cart(null);
        when(items.findLine(1L, 10L, null)).thenReturn(Optional.empty());
        when(items.countByCartId(1L)).thenReturn(0L);
        variant(10L, "500.00", "5.00", 200, true, 50);
        when(items.findByCartIdOrderByIdAsc(1L)).thenReturn(List.of(item(1, 10, 3)));

        CartView v = service.addItem(USER, "pub-10", 3, null);

        verify(items).save(Mockito.argThat(ci -> ci.getQuantity() == 3 && ci.getVariantId() == 10L));
        assertThat(v.totals().subtotal()).isEqualByComparingTo("1500.00");
        assertThat(v.totals().gstAmount()).isEqualByComparingTo("75.00");
        assertThat(v.totals().total()).isEqualByComparingTo("1575.00");
        assertThat(v.totals().shippingCalculated()).isFalse();
        assertThat(v.lines().get(0).variantPublicId()).isEqualTo("pub-10");
        assertThat(v.lines().get(0).gstAmount()).isEqualByComparingTo("75.00");
        assertThat(v.checkoutReady()).isTrue();
    }

    @Test
    void addingSameVariantAgainMergesQuantity() {
        cart(null);
        CartItem existing = item(1, 10, 2);
        when(items.findLine(1L, 10L, null)).thenReturn(Optional.of(existing));
        variant(10L, "500.00", "5.00", 200, true, 50);
        when(items.findByCartIdOrderByIdAsc(1L)).thenReturn(List.of(existing));

        service.addItem(USER, "pub-10", 3, null);

        assertThat(existing.getQuantity()).isEqualTo(5);
        verify(items, never()).save(any());
    }

    @Test
    void addBeyondAvailableStockIsRejected() {
        cart(null);
        when(items.findLine(1L, 10L, null)).thenReturn(Optional.empty());
        when(items.countByCartId(1L)).thenReturn(0L);
        variant(10L, "500.00", "5.00", 200, true, 4);

        assertThatThrownBy(() -> service.addItem(USER, "pub-10", 5, null))
                .hasFieldOrPropertyWithValue("code", "CART_INSUFFICIENT_STOCK");
        verify(items, never()).save(any());
    }

    @Test
    void unsellableVariantCannotBeAdded() {
        cart(null);
        when(items.findLine(1L, 10L, null)).thenReturn(Optional.empty());
        when(items.countByCartId(1L)).thenReturn(0L);
        variant(10L, "500.00", "5.00", 200, false, 50);

        assertThatThrownBy(() -> service.addItem(USER, "pub-10", 1, null))
                .hasFieldOrPropertyWithValue("code", "CART_VARIANT_UNAVAILABLE");
    }

    @Test
    void unknownPublicIdIsRejected() {
        when(pricer.resolveVariantId("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItem(USER, "nope", 1, null))
                .hasFieldOrPropertyWithValue("code", "CART_VARIANT_UNAVAILABLE");
    }

    @Test
    void quantityBelowOneIsRejected() {
        assertThatThrownBy(() -> service.addItem(USER, "pub-10", 0, null))
                .hasFieldOrPropertyWithValue("code", "CART_INVALID_QUANTITY");
    }

    @Test
    void couponGstAndShippingAreCombinedIntoTotal() {
        cart("SAVE10");
        variant(10L, "1000.00", "5.00", 200, true, 50);
        when(items.findByCartIdOrderByIdAsc(1L)).thenReturn(List.of(item(1, 10, 2)));
        when(coupons.evaluate(eq("SAVE10"), any(), any(Instant.class))).thenReturn(
                new AppliedCoupon("SAVE10", null, DiscountType.PERCENTAGE, new BigDecimal("200.00"), false));
        when(shipping.quote(eq("Haryana"), eq("122001"), eq(400), any())).thenReturn(
                new ShippingQuote("NCR", "Delhi NCR", new BigDecimal("60.00"), false, 2, 4));

        CartView v = service.getCart(USER, new ShippingDestination("Haryana", "122001"));

        // subtotal 2000 - 200 = 1800 taxable; GST 5% = 90; shipping 60 -> 1950
        assertThat(v.totals().subtotal()).isEqualByComparingTo("2000.00");
        assertThat(v.totals().taxableAmount()).isEqualByComparingTo("1800.00");
        assertThat(v.totals().gstAmount()).isEqualByComparingTo("90.00");
        assertThat(v.totals().shippingCharge()).isEqualByComparingTo("60.00");
        assertThat(v.totals().total()).isEqualByComparingTo("1950.00");
        assertThat(v.totals().totalWeightGrams()).isEqualTo(400);
    }

    @Test
    void freeShippingCouponZeroesTheShippingCharge() {
        cart("FREESHIP");
        variant(10L, "1000.00", "5.00", 200, true, 50);
        when(items.findByCartIdOrderByIdAsc(1L)).thenReturn(List.of(item(1, 10, 1)));
        when(coupons.evaluate(eq("FREESHIP"), any(), any(Instant.class))).thenReturn(
                new AppliedCoupon("FREESHIP", null, DiscountType.FREE_SHIPPING, BigDecimal.ZERO, true));
        when(shipping.quote(any(), any(), Mockito.anyInt(), any())).thenReturn(
                new ShippingQuote("NCR", "Delhi NCR", new BigDecimal("60.00"), false, 2, 4));

        CartView v = service.getCart(USER, new ShippingDestination("Haryana", "122001"));

        assertThat(v.totals().shippingCharge()).isEqualByComparingTo("0.00");
        assertThat(v.totals().total()).isEqualByComparingTo("1050.00"); // 1000 + 5% GST
    }

    @Test
    void expiredStoredCouponIsReportedNotThrown() {
        cart("OLD");
        variant(10L, "1000.00", "5.00", 200, true, 50);
        when(items.findByCartIdOrderByIdAsc(1L)).thenReturn(List.of(item(1, 10, 1)));
        when(coupons.evaluate(eq("OLD"), any(), any(Instant.class))).thenThrow(CouponException.expired());

        CartView v = service.getCart(USER, null);

        assertThat(v.coupon()).isNull();
        assertThat(v.couponError()).isNotBlank();
        assertThat(v.totals().discount()).isEqualByComparingTo("0.00");
    }

    @Test
    void outOfStockLineIsFlaggedAndBlocksCheckout() {
        cart(null);
        variant(10L, "500.00", "5.00", 200, true, 0);
        when(items.findByCartIdOrderByIdAsc(1L)).thenReturn(List.of(item(1, 10, 2)));

        CartView v = service.getCart(USER, null);

        assertThat(v.lines().get(0).issue()).isEqualTo(LineIssue.OUT_OF_STOCK);
        assertThat(v.checkoutReady()).isFalse();
    }

    @Test
    void removingUnknownItemGives404Code() {
        cart(null);
        when(items.findByIdAndCartId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeItem(USER, 99L, null))
                .hasFieldOrPropertyWithValue("code", "CART_ITEM_NOT_FOUND");
    }
}