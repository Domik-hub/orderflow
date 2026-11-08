package dev.domikhub.orderflow.cart;

import dev.domikhub.orderflow.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/cart")
public class CartController {
    private final CartService carts;
    private final CurrentUser current;
    public CartController(CartService carts, CurrentUser current) { this.carts = carts; this.current = current; }
    @GetMapping public CartDtos.View get(@AuthenticationPrincipal Jwt jwt) { return carts.get(current.require(jwt).getId()); }
    @PutMapping("/items/{productId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void set(@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId, @Valid @RequestBody CartDtos.Quantity input) {
        carts.set(current.require(jwt).getId(), productId, input.quantity());
    }
    @DeleteMapping("/items/{productId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId) { carts.remove(current.require(jwt).getId(), productId); }
    @DeleteMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal Jwt jwt) { carts.clear(current.require(jwt).getId()); }
}
