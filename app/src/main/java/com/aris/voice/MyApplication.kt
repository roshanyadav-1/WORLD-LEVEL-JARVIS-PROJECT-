package com.aris.voice

import android.app.Application
import android.content.Context
import android.content.Intent
import com.aris.voice.utilities.Logger
import com.android.billingclient.api.*
import com.aris.voice.intents.IntentRegistry
import com.aris.voice.intents.impl.DialIntent
import com.aris.voice.intents.impl.EmailComposeIntent
import com.aris.voice.intents.impl.ShareTextIntent
import com.aris.voice.intents.impl.ViewUrlIntent
import com.aris.voice.intents.impl.SendSMSIntent
import com.aris.voice.intents.impl.OpenMapsIntent
import com.aris.voice.intents.impl.SetAlarmIntent
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow
import com.aris.voice.core.*
import com.aris.voice.di.ArisServiceRegistry
import com.aris.voice.brain.*
import com.aris.voice.memory.*
import com.aris.voice.perception.*
import com.aris.voice.actions.*
import com.aris.voice.conversation.*
import com.aris.voice.learning.*
import com.aris.voice.tools.*
import com.aris.voice.overlay.*
import com.aris.voice.audio.*

class MyApplication : Application(), PurchasesUpdatedListener {

    private val applicationScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val initialReconnectDelayMs = 1000L

    companion object {
        lateinit var appContext: Context
            private set

        lateinit var billingClient: BillingClient
            private set

        private val _isBillingClientReady = MutableStateFlow(false)
        val isBillingClientReady: StateFlow<Boolean> = _isBillingClientReady.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        // Initialize Firebase Remote Config
        val remoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600L
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)


        billingClient = BillingClient.newBuilder(this)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        connectToBillingService()

        IntentRegistry.register(DialIntent())
        IntentRegistry.register(ViewUrlIntent())
        IntentRegistry.register(ShareTextIntent())
        IntentRegistry.register(EmailComposeIntent())
        IntentRegistry.register(SendSMSIntent())
        IntentRegistry.register(OpenMapsIntent())
        IntentRegistry.register(SetAlarmIntent())
        IntentRegistry.init(this)
        
        com.aris.voice.utilities.ApiKeyManager.init(this)
        
        initArisRegistry()

        // Cleanup expired memories on startup
        applicationScope.launch {
            try {
                com.aris.voice.data.MemoryManager.getInstance(appContext).cleanupExpiredMemories()
            } catch (e: Exception) {
                com.aris.voice.utilities.Logger.e("MyApplication", "Failed to cleanup memories", e)
            }
        }
    }

    private fun initArisRegistry() {
        val logger = AndroidArisLogger()
        ArisServiceRegistry.register(ArisLogger::class.java, logger)

        val config = AndroidArisConfig(this)
        ArisServiceRegistry.register(ArisConfig::class.java, config)

        val eventBus = DefaultArisEventBus()
        ArisServiceRegistry.register(ArisEventBus::class.java, eventBus)

        val memory = MemoryImpl(this)
        ArisServiceRegistry.register(IWorkingMemory::class.java, memory)
        ArisServiceRegistry.register(ILongTermMemory::class.java, memory)
        ArisServiceRegistry.register(ISemanticMemory::class.java, memory)
        ArisServiceRegistry.register(IEpisodicMemory::class.java, memory)
        ArisServiceRegistry.register(ISkillMemory::class.java, memory)

        val perception = PerceptionImpl(this)
        ArisServiceRegistry.register(IPerceptionEngine::class.java, perception)
        ArisServiceRegistry.register(IScreenAnalyzer::class.java, perception)
        ArisServiceRegistry.register(IAccessibilityParser::class.java, perception)
        ArisServiceRegistry.register(IDeviceStateMonitor::class.java, perception)

        val actions = ActionImpl(this, null)
        ArisServiceRegistry.register(IActionExecutor::class.java, actions)
        ArisServiceRegistry.register(IGestureExecutor::class.java, actions)
        ArisServiceRegistry.register(ISystemActionExecutor::class.java, actions)
        ArisServiceRegistry.register(IAppLauncher::class.java, actions)
        
        val planExecutor = PlanExecutorImpl(actions)
        ArisServiceRegistry.register(IPlanExecutor::class.java, planExecutor)

        val conversation = ConversationImpl(this)
        ArisServiceRegistry.register(IDialogueManager::class.java, conversation)
        ArisServiceRegistry.register(ITextToSpeech::class.java, conversation)
        ArisServiceRegistry.register(IConversationContext::class.java, conversation)

        val learning = LearningImpl()
        ArisServiceRegistry.register(IReflectionEngine::class.java, learning)
        ArisServiceRegistry.register(IPatternLearner::class.java, learning)

        val toolRegistry = ToolRegistryImpl()
        ArisServiceRegistry.register(IToolRegistry::class.java, toolRegistry)

        val overlay = OverlayRendererImpl(this)
        ArisServiceRegistry.register(IOverlayRenderer::class.java, overlay)

        val audio = AudioImpl()
        ArisServiceRegistry.register(IAudioStreamProvider::class.java, audio)
        ArisServiceRegistry.register(IWakeWordDetector::class.java, audio)
        ArisServiceRegistry.register(ISpeechRecognizer::class.java, audio)

        // Brain
        val intentAnalyzer = IntentAnalyzerImpl()
        val worldModel = WorldModelImpl()
        val contextBuilder = ContextBuilderImpl(worldModel)
        val taskPlanner = TaskPlannerImpl()
        val reasoningEngine = ReasoningEngineImpl()
        val riskValidator = RiskValidatorImpl()
        val toolSelector = ToolSelectorImpl()
        val decisionEngine = DecisionEngineImpl()
        val skillEngine = SkillEngineImpl()
        val orchestrator = BrainOrchestratorImpl(
            intentAnalyzer,
            contextBuilder,
            worldModel,
            taskPlanner,
            reasoningEngine,
            riskValidator,
            toolSelector,
            decisionEngine
        )

        ArisServiceRegistry.register(IIntentAnalyzer::class.java, intentAnalyzer)
        ArisServiceRegistry.register(IContextBuilder::class.java, contextBuilder)
        ArisServiceRegistry.register(IWorldModel::class.java, worldModel)
        ArisServiceRegistry.register(ITaskPlanner::class.java, taskPlanner)
        ArisServiceRegistry.register(IReasoningEngine::class.java, reasoningEngine)
        ArisServiceRegistry.register(IRiskValidator::class.java, riskValidator)
        ArisServiceRegistry.register(IToolSelector::class.java, toolSelector)
        ArisServiceRegistry.register(IDecisionEngine::class.java, decisionEngine)
        ArisServiceRegistry.register(ISkillEngine::class.java, skillEngine)
        ArisServiceRegistry.register(IBrainOrchestrator::class.java, orchestrator)

        logger.i("MyApplication", "All Project ARIS cognitive platform services successfully registered in ArisServiceRegistry.")
    }

    private fun connectToBillingService() {
        if (billingClient.isReady) {
            Logger.d("MyApplication", "BillingClient is already connected.")
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Logger.d("MyApplication", "BillingClient setup successfully.")
                    _isBillingClientReady.value = true
                    reconnectAttempts = 0
                } else {
                    Logger.e("MyApplication", "BillingClient setup failed: ${billingResult.debugMessage}")
                    _isBillingClientReady.value = false
                    retryConnectionWithBackoff()
                }
            }

            override fun onBillingServiceDisconnected() {
                Logger.w("MyApplication", "Billing service disconnected. Retrying...")
                _isBillingClientReady.value = false
                retryConnectionWithBackoff()
            }
        })
    }

    private fun retryConnectionWithBackoff() {
        if (reconnectAttempts < maxReconnectAttempts) {
            val delay = initialReconnectDelayMs * (2.0.pow(reconnectAttempts)).toLong()
            applicationScope.launch {
                delay(delay)
                reconnectAttempts++
                Logger.d("MyApplication", "Retrying connection, attempt #$reconnectAttempts")
                connectToBillingService()
            }
        } else {
            Logger.e("MyApplication", "Max reconnect attempts reached. Will not retry further.")
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        Logger.d("MyApplication", "Purchase update received")
        val intent = Intent("com.aris.voice.PURCHASE_UPDATED")
        intent.putExtra("response_code", billingResult.responseCode)
        intent.putExtra("debug_message", billingResult.debugMessage)
        appContext.sendBroadcast(intent)
    }
}