const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const CUSTOMER_UID = "customer_101";
const OTHER_CUSTOMER_UID = "customer_202";
const VENDOR_UID = "vendor_303";
const ADMIN_UID = "admin_yahia_999";
const ADMIN_EMAIL = "yahiakhamisasd@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function serverTs() {
  return new Date();
}

test("Unauthenticated user: cannot read offers, users, or orders", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("offers").where("isAvailableForCustomers", "==", true).get()
  );
  await assertFails(unauthDb.collection("users").doc(CUSTOMER_UID).get());
  await assertFails(unauthDb.collection("orders").get());
});

test("User profile: prevents self-assigning admin role or self-approving vendor and prevents cross-user PII read", async () => {
  const customerDb = testEnv
    .authenticatedContext(CUSTOMER_UID, {
      email: "ahmed@gmail.com",
      email_verified: true,
    })
    .firestore();
  const now = serverTs();

  // Attempt to self-assign admin role in /users -> MUST FAIL
  await assertFails(
    customerDb.collection("users").doc(CUSTOMER_UID).set({
      userId: CUSTOMER_UID,
      name: "أحمد محمد",
      phone: "",
      email: "ahmed@gmail.com",
      role: "admin",
      isApproved: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Attempt to self-approve vendor role in /users -> MUST FAIL
  await assertFails(
    customerDb.collection("users").doc(CUSTOMER_UID).set({
      userId: CUSTOMER_UID,
      name: "أحمد محمد",
      phone: "01012345678",
      email: "ahmed@gmail.com",
      role: "vendor",
      isApproved: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid customer profile -> SUCCEEDS
  await assertSucceeds(
    customerDb.collection("users").doc(CUSTOMER_UID).set({
      userId: CUSTOMER_UID,
      name: "أحمد محمد",
      phone: "",
      email: "ahmed@gmail.com",
      role: "customer",
      isApproved: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Another user trying to read PII -> MUST FAIL
  const otherDb = testEnv
    .authenticatedContext(OTHER_CUSTOMER_UID, {
      email: "other@gmail.com",
      email_verified: true,
    })
    .firestore();
  await assertFails(otherDb.collection("users").doc(CUSTOMER_UID).get());
});

test("Vendor Approval System: enforces isApproved=false on registration, blocks unapproved vendor offers, and lets Admin approve or reject", async () => {
  const now = serverTs();
  const vendorDb = testEnv
    .authenticatedContext(VENDOR_UID, {
      email: "store@gmail.com",
      email_verified: true,
    })
    .firestore();

  // 1. Vendor tries to register with isApproved: true -> MUST FAIL
  await assertFails(
    vendorDb.collection("vendors").doc(VENDOR_UID).set({
      vendorId: VENDOR_UID,
      ownerName: "الحاج محمود",
      storeName: "سوبر ماركت الهدى",
      activityType: "SUPERMARKET",
      phone: "01099887766",
      email: "store@gmail.com",
      isApproved: true,
      isSuspended: false,
      suspensionReason: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // 2. Vendor registers properly with isApproved: false -> SUCCEEDS
  await assertSucceeds(
    vendorDb.collection("vendors").doc(VENDOR_UID).set({
      vendorId: VENDOR_UID,
      ownerName: "الحاج محمود",
      storeName: "سوبر ماركت الهدى",
      activityType: "SUPERMARKET",
      phone: "01099887766",
      email: "store@gmail.com",
      isApproved: false,
      isSuspended: false,
      suspensionReason: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // 3. Vendor tries to self-approve via update -> MUST FAIL
  await assertFails(
    vendorDb.collection("vendors").doc(VENDOR_UID).update({
      isApproved: true,
      updatedAt: new Date(),
    })
  );

  // 4. Unapproved vendor tries to publish an offer -> MUST FAIL
  await assertFails(
    vendorDb.collection("offers").doc("offer_unapproved").set({
      vendorId: VENDOR_UID,
      storeName: "سوبر ماركت الهدى",
      activityType: "SUPERMARKET",
      title: "عرض زيت وسكر",
      description: "كرتونة توفير",
      originalPrice: 250,
      offerPrice: 199,
      stockQuantity: 10,
      isActive: true,
      isVendorSuspended: false,
      isAvailableForCustomers: true,
      imageBase64: "",
      defaultImageKey: "SUPERMARKET",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // 5. Verified Admin (yahiakhamisasd@gmail.com) approves vendor -> SUCCEEDS
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, {
      email: ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();
  await assertSucceeds(
    adminDb.collection("vendors").doc(VENDOR_UID).update({
      isApproved: true,
      updatedAt: new Date(),
    })
  );

  // 6. Now approved vendor CAN publish offer -> SUCCEEDS
  await assertSucceeds(
    vendorDb.collection("offers").doc("offer_approved").set({
      vendorId: VENDOR_UID,
      storeName: "سوبر ماركت الهدى",
      activityType: "SUPERMARKET",
      title: "عرض زيت وسكر",
      description: "كرتونة توفير",
      originalPrice: 250,
      offerPrice: 199,
      stockQuantity: 10,
      isActive: true,
      isVendorSuspended: false,
      isAvailableForCustomers: true,
      imageBase64: "",
      defaultImageKey: "SUPERMARKET",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // 7. Admin can also reject/delete a pending vendor request -> SUCCEEDS
  await assertSucceeds(adminDb.collection("vendors").doc(VENDOR_UID).delete());
});

test("Admin Google email security gate: blocks non-admin Google emails, blocks unverified email spoofing, and unlocks yahiakhamisasd@gmail.com", async () => {
  const now = serverTs();

  const nonAdminDb = testEnv
    .authenticatedContext(OTHER_CUSTOMER_UID, {
      email: "someoneelse@gmail.com",
      email_verified: true,
    })
    .firestore();
  await assertFails(nonAdminDb.collection("orders").get());

  const spoofedAdminDb = testEnv
    .authenticatedContext(ADMIN_UID, {
      email: ADMIN_EMAIL,
      email_verified: false,
    })
    .firestore();
  await assertFails(spoofedAdminDb.collection("orders").get());

  const verifiedAdminDb = testEnv
    .authenticatedContext(ADMIN_UID, {
      email: "YahiaKhamisAsd@gmail.com",
      email_verified: true,
    })
    .firestore();
  await assertSucceeds(
    verifiedAdminDb.collection("admins").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      adminEmail: ADMIN_EMAIL,
      role: "admin",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(verifiedAdminDb.collection("vendors").get());
});

test("Orders: terminal state locking blocks vendor from cancelling a DELIVERED order to evade commission", async () => {
  const now = serverTs();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("orders").doc("order_delivered_1").set({
      offerId: "offer_live",
      offerTitle: "نصف فرخة مشوية",
      vendorId: VENDOR_UID,
      storeName: "مشويات القرية",
      activityType: "RESTAURANT",
      customerId: CUSTOMER_UID,
      customerName: "محمود حسن",
      customerPhone: "01122334455",
      deliveryAddress: "شارع المسجد الكبير بجوار الصيدلية",
      notes: "",
      quantity: 2,
      unitOfferPrice: 135,
      totalPrice: 270,
      commissionAmount: 27,
      status: "DELIVERED",
      cancellationReason: "",
      dateKey: "2026-10-23",
      createdAt: now,
      updatedAt: now,
    });
  });

  const vendorDb = testEnv.authenticatedContext(VENDOR_UID).firestore();
  await assertFails(
    vendorDb.collection("orders").doc("order_delivered_1").update({
      status: "CANCELLED",
      cancellationReason: "العميل لم يرد",
      updatedAt: new Date(),
    })
  );
});
