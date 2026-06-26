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

        val finger = com.aris.voice.api.Finger(this)
        val actionAdapter = com.aris.voice.adapters.ActionAdapterImpl(this, finger)
        ArisServiceRegistry.register(com.aris.voice.adapters.IActionAdapter::class.java, actionAdapter)

        val actions = ActionImpl(this, actionAdapter)
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
        
        val learningEngine = LearningEngineImpl()
        ArisServiceRegistry.register(ILearningEngine::class.java, learningEngine)

        val toolRegistry = ToolRegistryImpl()
        ArisServiceRegistry.register(IToolRegistry::class.java, toolRegistry)

        val overlay = OverlayRendererImpl(this)
        ArisServiceRegistry.register(IOverlayRenderer::class.java, overlay)

        val audio = AudioImpl()
        ArisServiceRegistry.register(IAudioStreamProvider::class.java, audio)
        ArisServiceRegistry.register(IWakeWordDetector::class.java, audio)
        ArisServiceRegistry.register(ISpeechRecognizer::class.java, audio)

        // Providers
        val deviceStateProvider = com.aris.voice.brain.world.providers.DeviceStateProviderImpl(this)
        val uiStateProvider = com.aris.voice.brain.world.providers.UiStateProviderImpl(this)
        val environmentProvider = com.aris.voice.brain.world.providers.EnvironmentProviderImpl(this)
        val taskStateProvider = com.aris.voice.brain.world.providers.TaskStateProviderImpl()
        val capabilityStateProvider = com.aris.voice.brain.world.providers.CapabilityStateProviderImpl(this)

        ArisServiceRegistry.register(com.aris.voice.brain.world.providers.IDeviceStateProvider::class.java, deviceStateProvider)
        ArisServiceRegistry.register(com.aris.voice.brain.world.providers.IUiStateProvider::class.java, uiStateProvider)
        ArisServiceRegistry.register(com.aris.voice.brain.world.providers.IEnvironmentProvider::class.java, environmentProvider)
        ArisServiceRegistry.register(com.aris.voice.brain.world.providers.ITaskStateProvider::class.java, taskStateProvider)
        ArisServiceRegistry.register(com.aris.voice.brain.world.providers.ICapabilityStateProvider::class.java, capabilityStateProvider)

        // Brain
        val intentAnalyzer = IntentAnalyzerImpl()
        val worldModel = WorldModelImpl(
            deviceStateProvider = deviceStateProvider,
            uiStateProvider = uiStateProvider,
            environmentProvider = environmentProvider,
            taskStateProvider = taskStateProvider,
            capabilityStateProvider = capabilityStateProvider
        )
        val contextBuilder = ContextBuilderImpl(worldModel)
        
        val executionOrchestrator = ExecutionOrchestratorImpl(actions, worldModel)
        ArisServiceRegistry.register(IExecutionOrchestrator::class.java, executionOrchestrator)
        val taskPlanner = TaskPlannerImpl()
        val reasoningEngine = ReasoningEngineImpl()
        val riskValidator = RiskValidatorImpl()
        val toolSelector = ToolSelectorImpl()
        val decisionEngine = DecisionEngineImpl()
        val skillEngine = SkillEngineImpl()
        val memoryEngine = MemoryEngineImpl()
        val experienceEngine = ExperienceEngineImpl()
        ArisServiceRegistry.register(com.aris.voice.brain.IExperienceEngine::class.java, experienceEngine)
        val knowledgeEngine = KnowledgeEngineImpl()
        ArisServiceRegistry.register(com.aris.voice.brain.IKnowledgeEngine::class.java, knowledgeEngine)
        val llmBridge = com.aris.voice.llm.LlmBridgeImpl()
        ArisServiceRegistry.register(com.aris.voice.llm.ILlmBridge::class.java, llmBridge)
        
        val voiceAdapter = com.aris.voice.adapters.VoiceAdapterImpl(this)
        ArisServiceRegistry.register(com.aris.voice.adapters.IVoiceAdapter::class.java, voiceAdapter)
        
        val triggerAdapter = com.aris.voice.adapters.TriggerAdapterImpl(this)
        ArisServiceRegistry.register(com.aris.voice.adapters.ITriggerAdapter::class.java, triggerAdapter)
        
        val overlayAdapter = com.aris.voice.adapters.OverlayAdapterImpl(this)
        ArisServiceRegistry.register(com.aris.voice.adapters.IOverlayAdapter::class.java, overlayAdapter)
        
        val memoryStorageAdapter = com.aris.voice.adapters.MemoryStorageAdapterImpl(this)
        ArisServiceRegistry.register(com.aris.voice.adapters.IMemoryStorageAdapter::class.java, memoryStorageAdapter)
        
        val accessibilityAdapter = com.aris.voice.adapters.AccessibilityAdapterImpl(this)
        ArisServiceRegistry.register(com.aris.voice.adapters.IAccessibilityAdapter::class.java, accessibilityAdapter)
        
        val legacyGeminiProvider = com.aris.voice.adapters.LegacyGeminiProviderAdapter(this)
        llmBridge.registerProvider(legacyGeminiProvider)
        
        val audioRuntimeManager = com.aris.voice.runtime.AudioRuntimeManagerImpl(
            object : com.aris.voice.runtime.IAudioProvider {
                override suspend fun startAudioSession() = com.aris.voice.core.ArisResult.Success(Unit)
                override suspend fun stopAudioSession() = com.aris.voice.core.ArisResult.Success(Unit)
            }
        )
        ArisServiceRegistry.register(com.aris.voice.runtime.IAudioRuntimeManager::class.java, audioRuntimeManager)

        val speechInputProcessor = com.aris.voice.runtime.SpeechInputProcessorImpl(
            com.aris.voice.runtime.providers.LegacySTTProviderAdapter(this)
        )
        ArisServiceRegistry.register(com.aris.voice.runtime.ISpeechInputProcessor::class.java, speechInputProcessor)

        val voiceConversationManager = com.aris.voice.runtime.VoiceConversationManagerImpl()
        ArisServiceRegistry.register(com.aris.voice.runtime.IVoiceConversationManager::class.java, voiceConversationManager)

        val speechOutputProcessor = com.aris.voice.runtime.SpeechOutputProcessorImpl(
            com.aris.voice.runtime.providers.LegacyTTSProviderAdapter(this)
        )
        ArisServiceRegistry.register(com.aris.voice.runtime.ISpeechOutputProcessor::class.java, speechOutputProcessor)
        
        val orchestrator = BrainOrchestratorImpl(
            intentAnalyzer = intentAnalyzer,
            contextBuilder = contextBuilder,
            worldModel = worldModel,
            taskPlanner = taskPlanner,
            reasoningEngine = reasoningEngine,
            riskValidator = riskValidator,
            toolSelector = toolSelector,
            decisionEngine = decisionEngine,
            experienceEngine = experienceEngine,
            reflectionEngine = learning, // was named learning above
            learningEngine = learningEngine,
            knowledgeEngine = knowledgeEngine
        )
        
        val voiceOrchestrator = com.aris.voice.runtime.VoiceOrchestratorImpl(
            audioRuntimeManager,
            speechInputProcessor,
            voiceConversationManager,
            speechOutputProcessor,
            orchestrator,
            executionOrchestrator
        )
        ArisServiceRegistry.register(com.aris.voice.runtime.IVoiceOrchestrator::class.java, voiceOrchestrator)

        val voiceSessionManager = com.aris.voice.runtime.VoiceSessionManagerImpl(
            voiceOrchestrator,
            voiceConversationManager
        )
        ArisServiceRegistry.register(com.aris.voice.runtime.IVoiceSessionManager::class.java, voiceSessionManager)

        ArisServiceRegistry.register(IIntentAnalyzer::class.java, intentAnalyzer)
        ArisServiceRegistry.register(IContextBuilder::class.java, contextBuilder)
        ArisServiceRegistry.register(IWorldModel::class.java, worldModel)
        ArisServiceRegistry.register(ITaskPlanner::class.java, taskPlanner)
        ArisServiceRegistry.register(IReasoningEngine::class.java, reasoningEngine)
        ArisServiceRegistry.register(IRiskValidator::class.java, riskValidator)
        ArisServiceRegistry.register(IToolSelector::class.java, toolSelector)
        ArisServiceRegistry.register(IDecisionEngine::class.java, decisionEngine)
        ArisServiceRegistry.register(ISkillEngine::class.java, skillEngine)
        ArisServiceRegistry.register(IMemoryEngine::class.java, memoryEngine)
        ArisServiceRegistry.register(IExperienceEngine::class.java, experienceEngine)
        ArisServiceRegistry.register(IKnowledgeEngine::class.java, knowledgeEngine)
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
        intent.setPackage(packageName)
        intent.putExtra("response_code", billingResult.responseCode)
        intent.putExtra("debug_message", billingResult.debugMessage)
        appContext.sendBroadcast(intent)
    }
}