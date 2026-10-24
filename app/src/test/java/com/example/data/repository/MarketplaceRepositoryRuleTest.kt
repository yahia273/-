package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.ActivityType
import com.example.data.model.OrderStatus
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MarketplaceRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun fullMarketplaceLifecycle_vendorApprovalOfferOrderAndGoogleAdmin_succeeds() = runBlocking {
    val suffix = UUID.randomUUID().toString().take(8)
    val vendorEmail = "vendor_$suffix@gmail.com"
    val rejectedVendorEmail = "rejected_vendor_$suffix@gmail.com"
    val customerEmail = "customer_$suffix@gmail.com"

    // 1. Vendor registers -> automatically saved with isApproved = false
    val vendorUid = signInGoogleTestUser("vendor_sub_$suffix", vendorEmail)
    val repo = MarketplaceRepository(firestore)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo
        .saveVendorProfile(
          ownerName = "الحاج طارق $suffix",
          storeName = "مطعم البركة $suffix",
          activityType = ActivityType.RESTAURANT,
          phone = "01011223344",
          email = vendorEmail,
        )
        .getOrThrow()
    }

    val pendingVendorStore =
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeVendorStore(vendorUid).first { it != null } }!!
    assertFalse(pendingVendorStore.isApproved)
    assertEquals("الحاج طارق $suffix", pendingVendorStore.ownerName)

    val vendorUserProfile =
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeUserProfile(vendorUid).first { it != null } }!!
    assertFalse(vendorUserProfile.isApproved)

    // Unapproved vendor cannot create an offer
    val blockedOfferAttempt =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.createOffer(
          vendorStore = pendingVendorStore,
          title = "عرض غير مفعل",
          description = "محاولة قبل التفعيل",
          originalPrice = 150.0,
          offerPrice = 100.0,
          stockQuantity = 5L,
          imageBase64 = "",
          defaultImageKey = ActivityType.RESTAURANT,
        )
      }
    assertTrue(blockedOfferAttempt.isFailure)

    // Second vendor registers so we can also test Admin rejection
    val rejectedVendorUid = signInGoogleTestUser("rej_vendor_sub_$suffix", rejectedVendorEmail)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo
        .saveVendorProfile(
          ownerName = "محل تجريبي",
          storeName = "كافيه تجريبي $suffix",
          activityType = ActivityType.CAFE,
          phone = "01199887766",
          email = rejectedVendorEmail,
        )
        .getOrThrow()
    }
    val pendingRejectedStore =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeVendorStore(rejectedVendorUid).first { it != null }
      }!!

    // 2. Admin (yahiakhamisasd@gmail.com) sees pending vendor requests, approves first, rejects second
    signInGoogleTestUser("yahia_admin_$suffix", "yahiakhamisasd@gmail.com", emailVerified = true)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.ensureAdminSessionRecorded("yahiakhamisasd@gmail.com").getOrThrow()
    }
    val allVendorsBefore =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeAllVendorsForAdmin().first { list -> list.any { it.vendorId == vendorUid } }
      }
    assertTrue(allVendorsBefore.any { it.vendorId == vendorUid && !it.isApproved })

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.approveVendorAccount(pendingVendorStore).getOrThrow()
      repo.rejectVendorRequest(pendingRejectedStore).getOrThrow()
    }

    // 3. Approved vendor logs back in -> isApproved == true -> publishes quantity-based offer (stock = 1)
    signInGoogleTestUser("vendor_sub_$suffix", vendorEmail)
    val approvedVendorStore =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeVendorStore(vendorUid).first { it != null && it.isApproved }
      }!!
    assertTrue(approvedVendorStore.isApproved)

    val offerId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .createOffer(
            vendorStore = approvedVendorStore,
            title = "عرض وجبة مشويات",
            description = "نصف كيلو كفتة وشيش طاووق",
            originalPrice = 300.0,
            offerPrice = 200.0,
            stockQuantity = 1L,
            imageBase64 = "",
            defaultImageKey = ActivityType.RESTAURANT,
          )
          .getOrThrow()
      }

    // 4. Customer signs in & places order -> Dual notifications sent + stock becomes 0
    signInGoogleTestUser("cust_sub_$suffix", customerEmail)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.saveCustomerProfile("ياسر محمود", "", customerEmail).getOrThrow()
    }

    val availableOffers =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeCustomerAvailableOffers().first { list -> list.any { it.id == offerId } }
      }
    val targetOffer = availableOffers.first { it.id == offerId }

    val orderId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .placeOrderWithDualNotifications(
            offer = targetOffer,
            customerName = "ياسر محمود",
            customerPhone = "01233445566",
            deliveryAddress = "شارع البحر بجوار المدرسة",
            notes = "بدون شطة",
            requestedQuantity = 1L,
          )
          .getOrThrow()
      }
    assertTrue(orderId.isNotBlank())

    // Offer stock is now 0 -> disappears from customer available offers
    val offersAfterOrder =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeCustomerAvailableOffers().first { list -> list.none { it.id == offerId } }
      }
    assertTrue(offersAfterOrder.none { it.id == offerId })

    // 5. Vendor receives instant notification & marks order DELIVERED
    signInGoogleTestUser("vendor_sub_$suffix", vendorEmail)
    val vendorNotifications =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeUserNotifications(vendorUid).first { list -> list.any { it.orderId == orderId } }
      }
    assertTrue(vendorNotifications.any { it.orderId == orderId && it.type == "NEW_ORDER" })
    assertTrue(vendorNotifications.any { it.type == "VENDOR_APPROVED" })

    val vendorOrders =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeVendorOrders(vendorUid).first { list -> list.any { it.id == orderId } }
      }
    val liveOrder = vendorOrders.first { it.id == orderId }
    assertEquals(20.0, liveOrder.commissionAmount, 0.01)

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.updateVendorOrderStatus(liveOrder, OrderStatus.DELIVERED).getOrThrow()
    }

    // 6. Admin verifies live order delivery & dual notification copy
    signInGoogleTestUser("yahia_admin_$suffix", "yahiakhamisasd@gmail.com", emailVerified = true)
    val adminNotifs =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeAdminNotifications().first { list -> list.any { it.orderId == orderId } }
      }
    assertTrue(adminNotifs.any { it.orderId == orderId && it.targetChannel == "ADMIN" })

    val allAdminOrders =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeAllOrdersForAdmin().first { list -> list.any { it.id == orderId } }
      }
    assertEquals("DELIVERED", allAdminOrders.first { it.id == orderId }.status)
  }

  @Test
  fun nonAdminGoogleAndUnauthenticatedAccess_rejectedBySecurityRules() = runBlocking {
    val suffix = UUID.randomUUID().toString().take(8)
    val customerEmail = "cust_sec_$suffix@gmail.com"
    val otherGoogleEmail = "other_user_$suffix@gmail.com"

    // Customer creates profile
    val customerUid = signInGoogleTestUser("cust_sec_$suffix", customerEmail)
    val repo = MarketplaceRepository(firestore)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.saveCustomerProfile("سارة خالد", "01555667788", customerEmail).getOrThrow()
    }

    // Other verified Google user tries to read customer PII -> MUST FAIL with PERMISSION_DENIED
    signInGoogleTestUser("other_sec_$suffix", otherGoogleEmail)
    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeUserProfile(customerUid).first() }
      fail("Expected PERMISSION_DENIED when reading another user's profile")
    } catch (e: Exception) {
      val fsEx = e.unwrapFirestoreException()
      assertNotNull(fsEx)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
    }

    // Non-admin Google user tries to read Admin orders -> MUST FAIL with PERMISSION_DENIED
    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeAllOrdersForAdmin().first() }
      fail("Expected PERMISSION_DENIED when non-admin Google account lists all orders")
    } catch (e: Exception) {
      val fsEx = e.unwrapFirestoreException()
      assertNotNull(fsEx)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
    }

    // Unauthenticated caller -> MUST FAIL with PERMISSION_DENIED
    auth.signOut()
    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeCustomerAvailableOffers().first() }
      fail("Expected PERMISSION_DENIED for unauthenticated caller")
    } catch (e: Exception) {
      val fsEx = e.unwrapFirestoreException()
      assertNotNull(fsEx)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
    }
  }

  @Test
  fun activeListenersDuringSignOut_closeCleanlyWithoutThrowingError() = runBlocking {
    val suffix = UUID.randomUUID().toString().take(8)
    val customerEmail = "cust_signout_$suffix@gmail.com"
    val customerUid = signInGoogleTestUser("cust_signout_$suffix", customerEmail)
    val repo = MarketplaceRepository(firestore)

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.saveCustomerProfile("أحمد فؤاد", "01022334455", customerEmail).getOrThrow()
    }

    val collectedProfiles = mutableListOf<String>()
    val firstEmissionReady = CompletableDeferred<Unit>()

    val collectorJob = launch {
      repo.observeUserProfile(customerUid).collect { profile ->
        if (profile != null) {
          collectedProfiles.add(profile.name)
          firstEmissionReady.complete(Unit)
        }
      }
    }

    withTimeout(FLOW_TIMEOUT_MS) { firstEmissionReady.await() }
    assertEquals(listOf("أحمد فؤاد"), collectedProfiles)

    // Sign out while snapshot listener is still open -> must complete cleanly without throwing
    auth.signOut()
    withTimeout(FLOW_TIMEOUT_MS) { collectorJob.join() }
  }

  private companion object {
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
