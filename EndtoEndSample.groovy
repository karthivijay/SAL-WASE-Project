def "Registered user checkout should be allowed for EFC and gift card items, gift wrapped together, shipped through US standard ground using Kohl's card as tender type"() {
        when:
        shippingAddress = buildAddressFromMap(address1: "555 W Madison ST", address2: "1231", city: "Chicago", stateCode: "IL", zipCode: "60661", countryCode: "US", type: Address.AddressType.SHIPPING)
        sendAddContactRequest(billingAddress, phone.phoneNumber, true, false)
        sendAddContactRequest(shippingAddress, true, false)
        def contactResult = resultObject as ContactResult
        //addCollectionItemToCart(productId: 845524779843367, skus: 90120304)

        addProductToCart(productId: ProductConstants.PRODUCT_GIFTWARP_AND_NO_DSHIP_ID, size: ProductConstants.PRODUCT_GIFTWARP_AND_NO_DSHIP_SIZE, color: ProductConstants.PRODUCT_GIFTWARP_AND_NO_DSHIP_COLOR, quantity: 1, gift: "true")
        addProductToCart(productId: ProductConstants.PRODUCT_GIFTWARP_AND_NO_DSHIP_ID_1, size: ProductConstants.PRODUCT_GIFTWARP_AND_NO_DSHIP_SIZE_1, color: ProductConstants.PRODUCT_GIFTWARP_AND_NO_DSHIP_COLOR_1, quantity: 1, gift: "true")

        final CartItem cartItem = findCartItemInCart(shoppingCart, ProductConstants.PRODUCT_GIFTWARP_AND_NO_DSHIP_ID)
        final ShoppingCart updatedCart = shoppingCart.updateCartItem(0, cartItem.toBuilder().giftWrap(true).build());
        updateCartItemQuantities(new UpdateCartRequest.Builder().shoppingCart(updatedCart).requester(requester).build())

        final CartItem cartItem1 = findCartItemInCart(shoppingCart, ProductConstants.PRODUCT_GIFTWARP_AND_NO_DSHIP_ID_1)
        final ShoppingCart updatedCart1 = shoppingCart.updateCartItem(1, cartItem1.toBuilder().giftWrap(true).build());
        updateCartItemQuantities(new UpdateCartRequest.Builder().shoppingCart(updatedCart1).requester(requester).build())

        InventoryCheckResult checkResult = checkInventory(new UpdateCartRequest.Builder().shoppingCart(shoppingCart).requester(requester).build())


        proceedToCheckoutWithCart(shoppingCart: shoppingCart, user: user)
        then:
        assert resultObject.isSuccess()
        ProceedToCheckoutResult ptcr = resultObject as ProceedToCheckoutResult
        assert ptcr.inventoryCheckResult.shippingOptions != null
        assert !ptcr.inventoryCheckResult.shippingOptions.shippingCosts.isEmpty()
        assert shoppingCart.items.size() == 2
        assert shoppingCart.totalNumberOfItems == 2

        when:
        def checkoutReq = new UpdateBillingShippingRequest()
        checkoutReq.user = user
        checkoutReq.shippingOptions = ptcr.inventoryCheckResult.shippingOptions
        checkoutReq.shoppingCart = shoppingCart
        checkoutReq.shippingMethod = findShippingOption(ptcr.inventoryCheckResult.shippingOptions, USSTD).id
        checkoutReq.shippingAddressSameAsBillingAddress = false
        checkoutReq.billingContact = new Contact.Builder().names(name).address(billingAddress).phone(phone).email(testEmail).build();
        checkoutReq.shippingContact = new Contact.Builder().names(name).address(shippingAddress).phone(phone).build();

        and:

        addCreditCardWith(user: user,
                cardHolderName: name.toString(),
                cardNumber: PaymentConstants.KohlsChargeCard,
                cardBrand: CreditCardBrand.KOHLS_CHARGE

        )

        then:
        def checkoutRes = registeredCheckout(checkoutReq)
        checkoutRes.success
        checkoutRes.updatedShoppingCart.items.size() == 2
        checkoutRes.updatedShoppingCart.totalNumberOfItems == 2
        checkoutRes.billingAndShippingContacts.primaryShippingContact.address.address1 == "555 W Madison ST"
        checkoutRes.billingAndShippingContacts.primaryBillingContact.address.address1 == "7 Sycamore St"
        def subTotal = checkoutReq.shoppingCart.subTotal

        when:

        GiftWrapMessageRequest giftWrapMessageRequest = buildGiftWrapMessageRequestMaster(checkoutRes, false, true, true)
        GiftWrapMessageResponse giftWrapMessageResponse = markItemAsGiftWrapped(giftWrapMessageRequest)

        then:
        giftWrapMessageResponse.success
        // gift wrapping adds an additional item to the cart (one with a 'special' gift wrap sku)
        giftWrapMessageResponse.shoppingCart.items.size() == 3
        giftWrapMessageResponse.shoppingCart.totalNumberOfItems == 2
        giftWrapMessageResponse.shoppingCart.lineItemCount == 3
        giftWrapMessageResponse.shoppingCart.getAllGiftWrappedOnlyItems().size() == 2
        giftWrapMessageResponse.shoppingCart.giftWrapCharges == 4.95
        giftWrapMessageResponse.shoppingCart.subTotal == subTotal

        when:
        def primaryCreditCard = getCreditCards(user).primaryCreditCard;
        println(primaryCreditCard);
        submitPaymentRegWith(shoppingCart: shoppingCart, creditCard: primaryCreditCard, creditCards: [], user: user, isRegistered: true)

        then:
        assert resultObject.isSuccess()
        SubmitPaymentResult paymentResult = resultObject as SubmitPaymentResult
        def order = paymentResult.order
        def creditCards = paymentResult.creditCards
        def contacts = contactResult.contacts
        Double[] amounts = amtCalc(paymentResult: paymentResult)
        Double subtotalAfterLID = amounts[0].round(2)
        Double shipAmt = amounts[1].round(2)
        Double subtotalBeforeLID = amounts[2].round(2)
        Double giftWrapCharge = amounts[3].round(2)
        Double discount = paymentResult.order.discountAmount
        Double taxAmt = amounts[4].round(2)
        Double total = amounts[5].round(2)

        when:
        submitOrderReviewRequestWith(order: order, user: user, creditCards: creditCards, billingAndShippingContacts: contacts, shoppingCart: shoppingCart)

        then:
        assert resultObject.isSuccess()
        OrderAndReviewResult OrderSubmitResult = resultObject as OrderAndReviewResult
        validateOrder(result: OrderSubmitResult, subTotalBeforeLID: subtotalBeforeLID, subTotalAfterLID: subtotalAfterLID, taxAmt: taxAmt, shippingAmt: shipAmt,
                total: total, discount: (discount), status: submittedStatus)
    }
