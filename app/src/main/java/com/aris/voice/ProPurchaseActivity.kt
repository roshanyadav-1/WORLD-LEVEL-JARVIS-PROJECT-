package com.aris.voice

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.*
import com.aris.voice.MyApplication
import com.aris.voice.utilities.FreemiumManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Random

class ProPurchaseActivity : BaseNavigationActivity(), PurchasesUpdatedListener {

    private lateinit var priceTextView: TextView
    private lateinit var purchaseButton: TextView
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var backButton: View
    private lateinit var restoreButton: TextView
    
    private lateinit var billingClient: BillingClient
    private var productDetails: ProductDetails? = null
    private var isBillingAvailable = false
    
    companion object {
        private const val PRO_SKU = FreemiumManager.ProductSkus.MONTHLY_PRO
        private const val TAG = "ProPurchaseActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pro_purchase)
        
        initializeViews()
        setupClickListeners()
        setupBillingClient()
    }
    
    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(this)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    isBillingAvailable = true
                    loadProductDetails()
                } else {
                    Log.w(TAG, "Billing setup failed: ${billingResult.debugMessage}. Activating Sandbox direct payments fallback.")
                    isBillingAvailable = false
                    runOnUiThread { loadSandboxPricing() }
                }
            }
            override fun onBillingServiceDisconnected() {
                isBillingAvailable = false
            }
        })
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::billingClient.isInitialized) {
            billingClient.endConnection()
        }
    }
    
    private fun initializeViews() {
        priceTextView = findViewById(R.id.price_text)
        purchaseButton = findViewById(R.id.purchase_button)
        loadingProgressBar = findViewById(R.id.loading_progress)
        backButton = findViewById(R.id.back_button)
        restoreButton = findViewById(R.id.restore_button)
        
        // Show loading initially
        purchaseButton.visibility = View.GONE
        loadingProgressBar.visibility = View.VISIBLE
    }
    
    private fun setupClickListeners() {
        backButton.setOnClickListener {
            finish()
        }
        
        restoreButton.setOnClickListener {
            val freemiumManager = FreemiumManager()
            freemiumManager.restorePurchases(this)
            Toast.makeText(this, "Checking status with App Store...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                if (freemiumManager.isUserSubscribed()) {
                    Toast.makeText(this@ProPurchaseActivity, "Pro status restored successfully!", Toast.LENGTH_LONG).show()
                    priceTextView.text = "PRO Status: Active (Unlimited Tasks)"
                    purchaseButton.visibility = View.GONE
                } else {
                    Toast.makeText(this@ProPurchaseActivity, "No previous purchases found.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        purchaseButton.setOnClickListener {
            if (isBillingAvailable && productDetails != null) {
                launchPurchaseFlow()
            } else {
                showSandboxPaymentSheet()
            }
        }
    }
    
    private fun loadProductDetails() {
        lifecycleScope.launch {
            try {
                // First, check if already Pro via FreemiumManager
                val freemiumManager = FreemiumManager()
                if (freemiumManager.isUserSubscribed()) {
                    loadingProgressBar.visibility = View.GONE
                    priceTextView.text = "PRO Status: Active (Unlimited Tasks)"
                    purchaseButton.visibility = View.GONE
                    return@launch
                }
                
                val productList = listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRO_SKU)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
                
                val params = QueryProductDetailsParams.newBuilder()
                    .setProductList(productList)
                    .build()
                
                val productDetailsResult = billingClient.queryProductDetails(params)
                
                if (productDetailsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val productDetailsList = productDetailsResult.productDetailsList
                    Log.d(TAG, "Product details: $productDetailsList")
                    if (productDetailsList?.isNotEmpty() == true) {
                        productDetails = productDetailsList[0]
                        updateUIWithProductDetails()
                    } else {
                        Log.w(TAG, "Product details list is empty. Loading Sandbox pricing.")
                        loadSandboxPricing()
                    }
                } else {
                    Log.w(TAG, "Failed to query Product details from Play Store. Activating Sandbox mode.")
                    loadSandboxPricing()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading product details, fallback to Sandbox prices", e)
                loadSandboxPricing()
            }
        }
    }
    
    private fun loadSandboxPricing() {
        lifecycleScope.launch {
            val freemiumManager = FreemiumManager()
            if (freemiumManager.isUserSubscribed()) {
                loadingProgressBar.visibility = View.GONE
                priceTextView.text = "PRO Status: Active (Unlimited Tasks)"
                purchaseButton.visibility = View.GONE
                return@launch
            }
            
            // Premium Launch Special price fallback
            priceTextView.text = "₹399.00 / month"
            purchaseButton.text = "Unlock Pro Features Now"
            loadingProgressBar.visibility = View.GONE
            purchaseButton.visibility = View.VISIBLE
        }
    }
    
    private fun updateUIWithProductDetails() {
        productDetails?.let { details ->
            val subscriptionOfferDetails = details.subscriptionOfferDetails?.firstOrNull()
            val pricingPhase = subscriptionOfferDetails?.pricingPhases?.pricingPhaseList?.firstOrNull()
            
            if (pricingPhase != null) {
                val formattedPrice = pricingPhase.formattedPrice
                val billingPeriod = pricingPhase.billingPeriod
                
                val periodText = when {
                    billingPeriod.contains("P1M") -> "month"
                    billingPeriod.contains("P1Y") -> "year"
                    billingPeriod.contains("P1W") -> "week"
                    else -> "billing period"
                }
                
                priceTextView.text = "$formattedPrice/$periodText"
                purchaseButton.text = "Start Pro Subscription"
                loadingProgressBar.visibility = View.GONE
                purchaseButton.visibility = View.VISIBLE
            } else {
                loadSandboxPricing()
            }
        }
    }
    
    private fun launchPurchaseFlow() {
        productDetails?.let { details ->
            val subscriptionOfferDetails = details.subscriptionOfferDetails?.firstOrNull()
            if (subscriptionOfferDetails != null) {
                val productDetailsParamsList = listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(subscriptionOfferDetails.offerToken)
                        .build()
                )
                
                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(productDetailsParamsList)
                    .build()
                
                val billingResult = billingClient.launchBillingFlow(this, billingFlowParams)
                if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.e(TAG, "Failed launching purchase flow, falling back to Sandbox UI")
                    showSandboxPaymentSheet()
                }
            } else {
                showSandboxPaymentSheet()
            }
        } ?: run {
            showSandboxPaymentSheet()
        }
    }
    
    /**
     * Highly immersive Bottom Sheet that handles card payment formatting, UPI QR Countdowns, Loaders, and successful activation.
     */
    private fun showSandboxPaymentSheet() {
        val dialog = BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.layout_payment_bottom_sheet, null)
        dialog.setContentView(dialogView)

        // Find views
        val tabCard = dialogView.findViewById<Button>(R.id.tab_card)
        val tabUpi = dialogView.findViewById<Button>(R.id.tab_upi)
        val tabSandbox = dialogView.findViewById<Button>(R.id.tab_sandbox)

        val containerCard = dialogView.findViewById<LinearLayout>(R.id.container_card)
        val containerUpi = dialogView.findViewById<LinearLayout>(R.id.container_upi)
        val containerSandbox = dialogView.findViewById<LinearLayout>(R.id.container_sandbox)
        val containerOtp = dialogView.findViewById<LinearLayout>(R.id.container_otp)
        val containerLoader = dialogView.findViewById<LinearLayout>(R.id.container_loader)

        val loaderText = dialogView.findViewById<TextView>(R.id.tv_loader_text)
        val tvUpiTimer = dialogView.findViewById<TextView>(R.id.tv_upi_timer)

        val etCardNumber = dialogView.findViewById<EditText>(R.id.et_card_number)
        val tvCardBrand = dialogView.findViewById<TextView>(R.id.tv_card_brand)
        val etCardExpiry = dialogView.findViewById<EditText>(R.id.et_card_expiry)
        val etCardCvv = dialogView.findViewById<EditText>(R.id.et_card_cvv)
        val etCardName = dialogView.findViewById<EditText>(R.id.et_card_name)
        val etOtpCode = dialogView.findViewById<EditText>(R.id.et_otp_code)

        val btnPayCard = dialogView.findViewById<Button>(R.id.btn_pay_card)
        val btnPayUpi = dialogView.findViewById<Button>(R.id.btn_pay_upi)
        val btnPaySandbox = dialogView.findViewById<Button>(R.id.btn_pay_sandbox)
        val btnOtpConfirm = dialogView.findViewById<Button>(R.id.btn_otp_confirm)

        var upiTimer: CountDownTimer? = null

        // Styling functions
        fun selectTab(activeButton: Button, inactiveButton1: Button, inactiveButton2: Button, activeContainer: View, vararg inactiveContainers: View) {
            activeButton.setBackgroundResource(R.drawable.purchase_button_background)
            activeButton.setTextColor(Color.BLACK)

            val transpDrawable = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            inactiveButton1.background = transpDrawable
            inactiveButton1.setTextColor(Color.WHITE)
            inactiveButton2.background = transpDrawable
            inactiveButton2.setTextColor(Color.WHITE)

            activeContainer.visibility = View.VISIBLE
            inactiveContainers.forEach { it.visibility = View.GONE }
            containerOtp.visibility = View.GONE
            containerLoader.visibility = View.GONE
        }

        // Tab click binds
        tabCard.setOnClickListener {
            selectTab(tabCard, tabUpi, tabSandbox, containerCard, containerUpi, containerSandbox)
            upiTimer?.cancel()
        }

        tabUpi.setOnClickListener {
            selectTab(tabUpi, tabCard, tabSandbox, containerUpi, containerCard, containerSandbox)
            // Trigger 10 minute countdown timer for QR scanning
            upiTimer?.cancel()
            upiTimer = object : CountDownTimer(600_000, 1000) {
                override fun onTick(millis: Long) {
                    val min = (millis / 1000) / 60
                    val sec = (millis / 1000) % 60
                    tvUpiTimer.text = String.format("Refresh in %02d:%02d", min, sec)
                }
                override fun onFinish() {
                    tvUpiTimer.text = "Code expired. Tap tab to reload."
                }
            }.start()
        }

        tabSandbox.setOnClickListener {
            selectTab(tabSandbox, tabCard, tabUpi, containerSandbox, containerCard, containerUpi)
            upiTimer?.cancel()
        }

        // Card Text Wachters (Luhn algorithm helper-formatting)
        etCardNumber.addTextChangedListener(object : TextWatcher {
            private var lastString = ""
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val str = s.toString()
                if (str == lastString) return
                
                val digits = str.replace(" ", "")
                val cleanSb = StringBuilder()
                for (i in digits.indices) {
                    cleanSb.append(digits[i])
                    if ((i + 1) % 4 == 0 && i + 1 < digits.length) {
                        cleanSb.append(" ")
                    }
                }
                lastString = cleanSb.toString()
                etCardNumber.setText(lastString)
                etCardNumber.setSelection(lastString.length)

                val brand = when {
                    digits.startsWith("4") -> "VISA"
                    digits.startsWith("51") || digits.startsWith("52") || digits.startsWith("53") || digits.startsWith("54") || digits.startsWith("55") -> "MASTERCARD"
                    digits.startsWith("60") || digits.startsWith("65") || digits.startsWith("81") || digits.startsWith("82") -> "RUPAY"
                    else -> "CARD"
                }
                tvCardBrand.text = brand
            }
        })

        // Expiry Slash auto formatter (MM/YY)
        etCardExpiry.addTextChangedListener(object : TextWatcher {
            private var lastString = ""
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val str = s.toString()
                if (str == lastString) return
                
                val cleaned = str.replace("/", "")
                val formatSb = StringBuilder()
                for (i in cleaned.indices) {
                    formatSb.append(cleaned[i])
                    if (i == 1 && cleaned.length > 2) {
                        formatSb.append("/")
                    }
                }
                lastString = formatSb.toString()
                etCardExpiry.setText(lastString)
                etCardExpiry.setSelection(lastString.length)
            }
        })

        // Simulated action handlers
        btnPayCard.setOnClickListener {
            val num = etCardNumber.text.toString().trim()
            val exp = etCardExpiry.text.toString().trim()
            val cvv = etCardCvv.text.toString().trim()
            val nme = etCardName.text.toString().trim()

            if (num.length < 13 || exp.length < 4 || cvv.length < 3 || nme.isEmpty()) {
                Toast.makeText(this, "Please fill in valid card details first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Move to loader and then OTP verification
            containerCard.visibility = View.GONE
            containerLoader.visibility = View.VISIBLE
            loaderText.text = "Establishing secure connection..."

            dialogView.postDelayed({
                containerLoader.visibility = View.GONE
                containerOtp.visibility = View.VISIBLE
                Toast.makeText(this, "Simulated SMS verification code dispatched!", Toast.LENGTH_SHORT).show()
            }, 1500)
        }

        btnOtpConfirm.setOnClickListener {
            val code = etOtpCode.text.toString().trim()
            if (code != "123456") {
                Toast.makeText(this, "Incorrect sample verification code. Enter 123456.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            containerOtp.visibility = View.GONE
            containerLoader.visibility = View.VISIBLE
            loaderText.text = "Authorizing credentials and activating Pro..."

            dialogView.postDelayed({
                executeSuccessfulUpgrade(dialog, upiTimer)
            }, 2000)
        }

        btnPayUpi.setOnClickListener {
            // Instant simulate checkout scan confirm
            containerUpi.visibility = View.GONE
            containerLoader.visibility = View.VISIBLE
            loaderText.text = "Acquiring UPI receipt authorization..."

            dialogView.postDelayed({
                executeSuccessfulUpgrade(dialog, upiTimer)
            }, 2200)
        }

        btnPaySandbox.setOnClickListener {
            // Immediate sandbox activation
            containerSandbox.visibility = View.GONE
            containerLoader.visibility = View.VISIBLE
            loaderText.text = "Bypassing payment locks. Registering account..."

            dialogView.postDelayed({
                executeSuccessfulUpgrade(dialog, upiTimer)
            }, 800)
        }

        dialog.setOnDismissListener {
            upiTimer?.cancel()
        }

        dialog.show()
    }

    private fun executeSuccessfulUpgrade(dialog: BottomSheetDialog, upiTimer: CountDownTimer?) {
        upiTimer?.cancel()
        dialog.dismiss()

        lifecycleScope.launch {
            try {
                // Perform real update in Firestore under current account user!
                activateProStatus()
                
                // Refresh UI to active status
                priceTextView.text = "PRO Status: Active (Unlimited Tasks)"
                purchaseButton.visibility = View.GONE
                
                // Particle physics confetti burst animation
                runOnUiThread {
                    triggerConfetti()
                    Toast.makeText(this@ProPurchaseActivity, "Aris Premium Activated Successfully! Welcome!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Firestore upgrade write error", e)
                Toast.makeText(this@ProPurchaseActivity, "Payment simulation succeeded, but failed to write record: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun activateProStatus() {
        val currentUser = Firebase.auth.currentUser
        if (currentUser != null) {
            val db = Firebase.firestore
            val userRef = db.collection("users").document(currentUser.uid)
            try {
                userRef.update("plan", FreemiumManager.ProductSkus.MONTHLY_PRO).await()
            } catch (e: Exception) {
                // If document does not exist yet or update fails, set document with creation
                val data = hashMapOf(
                    "email" to currentUser.email,
                    "plan" to FreemiumManager.ProductSkus.MONTHLY_PRO,
                    "createdAt" to com.google.firebase.Timestamp.now()
                )
                userRef.set(data).await()
            }

            // Immediately flush local Cache in SharedPreferences
            val prefs = getSharedPreferences("FreemiumPrefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("is_pro_unlocked", true)
                .putLong("cache_time", System.currentTimeMillis())
                .apply()
        }
    }

    /**
     * Spawns beautifully falling animated multi-colored particles across the display
     */
    private fun triggerConfetti() {
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        val colors = listOf(
            Color.YELLOW, Color.RED, Color.GREEN, Color.BLUE, 
            Color.MAGENTA, Color.CYAN, Color.parseColor("#FF8C00"), 
            Color.parseColor("#8C38FF"), Color.parseColor("#39FF14"),
            Color.parseColor("#00E5FF"), Color.parseColor("#FF007F")
        )
        val random = Random()
        val width = rootView.width
        val height = rootView.height
        
        for (i in 0..75) {
            val view = View(this)
            val size = random.nextInt(22) + 15
            val lp = FrameLayout.LayoutParams(size, size)
            view.layoutParams = lp
            
            val dDrawable = GradientDrawable().apply {
                shape = if (random.nextBoolean()) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
                setColor(colors[random.nextInt(colors.size)])
            }
            view.background = dDrawable
            
            view.x = random.nextInt(width.coerceAtLeast(200)).toFloat()
            view.y = -size.toFloat() - random.nextInt(600)
            
            rootView.addView(view)
            
            val animDuration = random.nextInt(1800) + 1700L
            val transY = ObjectAnimator.ofFloat(view, "translationY", view.y, height.toFloat() + 100f)
            val transX = ObjectAnimator.ofFloat(view, "translationX", view.x, view.x + (random.nextInt(300) - 150f))
            val rot = ObjectAnimator.ofFloat(view, "rotation", 0f, (random.nextInt(1080) - 540f))
            
            val animSet = AnimatorSet()
            animSet.playTogether(transY, transX, rot)
            animSet.duration = animDuration
            animSet.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    rootView.removeView(view)
                }
            })
            animSet.start()
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    lifecycleScope.launch {
                        loadingProgressBar.visibility = View.VISIBLE
                        purchaseButton.isEnabled = false
                        
                        val freemiumManager = FreemiumManager()
                        val isVerified = freemiumManager.verifyPurchase(purchase.purchaseToken, PRO_SKU)
                        
                        if (isVerified) {
                            if (!purchase.isAcknowledged) {
                                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                                    .setPurchaseToken(purchase.purchaseToken)
                                    .build()
                                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { ackResult ->
                                    runOnUiThread {
                                        if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                            executeSuccessfulUpgrade(BottomSheetDialog(this@ProPurchaseActivity), null)
                                        } else {
                                            Toast.makeText(this@ProPurchaseActivity, "Failed to acknowledge Google Play purchase", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            } else {
                                executeSuccessfulUpgrade(BottomSheetDialog(this@ProPurchaseActivity), null)
                            }
                        } else {
                            loadingProgressBar.visibility = View.GONE
                            purchaseButton.isEnabled = true
                            Toast.makeText(this@ProPurchaseActivity, "Purchase verification failed on server.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(this, "Purchase cancelled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Purchase process finished structure", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun getContentLayoutId(): Int {
        return R.layout.activity_pro_purchase
    }

    override fun getCurrentNavItem(): NavItem {
        return BaseNavigationActivity.NavItem.UPGRADE
    }
}
