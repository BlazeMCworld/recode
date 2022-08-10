package io.github.homchom.recode.init

typealias ModuleAction<T> = T.() -> Unit

/**
 * Constructs a basic weak [RModule].
 */
fun weakModule(
    dependencies: List<ModuleHandle>,
    onLoad: ModuleAction<RModule>?,
    onEnable: ModuleAction<RModule>?,
    onDisable: ModuleAction<RModule>?
): BaseModule {
    return WeakModule(onLoad, onEnable, onDisable).withDependencies(dependencies)
}

/**
 * Constructs a basic strong [RModule].
 */
fun strongModule(
    dependencies: List<ModuleHandle>,
    onLoad: ModuleAction<RModule>?,
    onEnable: ModuleAction<RModule>?,
    onDisable: ModuleAction<RModule>?
): BaseModule {
    return StrongModule(WeakModule(onLoad, onEnable, onDisable).withDependencies(dependencies))
}

/**
 * A basic [RModule] that also acts as a [ModuleHandle].
 */
interface BaseModule : RModule, ModuleHandle {
    override fun addAsDependency(to: ModuleView) {
        to.addDependency(this)
    }
}

private fun <T : RModule> T.withDependencies(dependencies: List<ModuleHandle>) = apply {
    for (handle in dependencies) handle.addAsDependency(this)
}

private class WeakModule(
    private val onLoad: ModuleAction<RModule>?,
    private val onEnable: ModuleAction<RModule>?,
    private val onDisable: ModuleAction<RModule>?
) : BaseModule {
    override var isLoaded = false
        private set
    override var isEnabled = false
        private set

    override val dependencies: List<RModule> get() = _dependencies
    private val _dependencies = mutableListOf<RModule>()

    val usages: Set<RModule> get() = _usages
    private val _usages = mutableSetOf<RModule>()

    @ModuleActiveState
    override fun load() {
        check(!isLoaded) { "Module is already loaded" }
        isLoaded = true
        onLoad?.invoke(this)
    }

    @ModuleActiveState
    override fun enable() {
        check(!isEnabled) { "Module is already enabled" }
        tryLoad()
        for (module in dependencies) module.addUsage(this)
        isEnabled = true
        onEnable?.invoke(this)
    }

    @ModuleActiveState
    override fun disable() {
        check(isEnabled) { "Module is already disabled" }
        for (module in dependencies) module.removeUsage(this)
        isEnabled = false
        onDisable?.invoke(this)
    }

    override fun addDependency(module: RModule) {
        _dependencies += module
    }

    @ModuleActiveState
    override fun addUsage(module: RModule) {
        if (!isEnabled) enable()
        _usages += module
    }

    @ModuleActiveState
    override fun removeUsage(module: RModule) {
        _usages -= module
    }
}

@JvmInline
private value class StrongModule(
    private val impl: WeakModule
) : BaseModule by impl {
    @ModuleActiveState
    override fun removeUsage(module: RModule) {
        impl.removeUsage(module)
        if (impl.usages.isEmpty()) disable()
    }
}