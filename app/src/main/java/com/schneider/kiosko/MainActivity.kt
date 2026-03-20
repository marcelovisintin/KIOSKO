package com.schneider.kiosko

import android.animation.ObjectAnimator
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.schneider.kiosko.databinding.ActivityMainBinding
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private companion object {
        private const val ADMIN_USERNAME = "kadmin"
    }

    private data class PolicySignature(
        val allowedPackages: Set<String>,
        val allowSystemUi: Boolean,
        val adminModeEnabled: Boolean,
    )

    private enum class SessionState {
        LOCKED,
        USER,
        ADMIN,
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AllowedAppsAdapter
    private lateinit var policyController: KioskPolicyController
    private var config: KioskConfig = KioskConfig(
        allowedPackages = emptySet(),
        allowSystemUi = false,
        adminModeEnabled = false,
        pinRequired = true,
        userPin = "1234",
        adminPin = "2026",
    )
    private var sessionState: SessionState = SessionState.LOCKED
    private val pinBuffer = StringBuilder(4)

    private var userProfiles: MutableList<KioskUserProfile> = mutableListOf()
    private var webLinks: MutableList<KioskWebLink> = mutableListOf()
    private var currentUserId: String? = null
    private var selectedAdminUserId: String? = null
    private var isUpdatingUserSpinner = false
    private var effectivePolicyPackages: Set<String> = emptySet()
    private var lastPolicySignature: PolicySignature? = null
    private var lastPolicyResult: PolicyApplyResult = PolicyApplyResult.DeviceOwnerMissing

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        policyController = KioskPolicyController(this)
        adapter = AllowedAppsAdapter(::openAllowedApp)

        binding.appsRecyclerView.layoutManager = GridLayoutManager(this, calculateSpanCount())
        binding.appsRecyclerView.adapter = adapter

        configureInteractions()
        renderSession()
        updatePinDots(isError = false)
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun configureInteractions() {
        binding.userSwitchAccountButton.setOnClickListener {
            currentUserId = null
            sessionState = if (config.pinRequired) SessionState.LOCKED else SessionState.USER
            resetPinState()
            clearLoginIdentity()
            renderSession()
            refreshState()
        }
        binding.adminApplyPoliciesButton.setOnClickListener { refreshState(forcePolicyApply = true) }
        binding.adminLogoutButton.setOnClickListener {
            currentUserId = null
            sessionState = if (config.pinRequired) SessionState.LOCKED else SessionState.USER
            resetPinState()
            clearLoginIdentity()
            renderSession()
            refreshState()
        }
        binding.adminExitKioskButton.setOnClickListener { exitKioskCompletely() }
        binding.adminAddUserButton.setOnClickListener { addUserFromAdminInputs() }
        binding.adminDeleteUserButton.setOnClickListener { deleteSelectedAdminUser() }
        binding.adminAddWebLinkButton.setOnClickListener { addWebLinkFromAdminInputs() }

        binding.adminUserSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isUpdatingUserSpinner) return
                selectedAdminUserId = userProfiles.getOrNull(position)?.id
                renderAdminWebLinks()
                renderAdminUserPermissions()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                if (isUpdatingUserSpinner) return
                selectedAdminUserId = null
                renderAdminWebLinks()
                renderAdminUserPermissions()
            }
        }

        setupPinButtons()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Bloqueado en kiosko: salida solo por "Salir de kiosko".
            }
        })
    }

    private fun setupPinButtons() {
        val digitButtons = mapOf(
            binding.pinKey0 to 0,
            binding.pinKey1 to 1,
            binding.pinKey2 to 2,
            binding.pinKey3 to 3,
            binding.pinKey4 to 4,
            binding.pinKey5 to 5,
            binding.pinKey6 to 6,
            binding.pinKey7 to 7,
            binding.pinKey8 to 8,
            binding.pinKey9 to 9,
        )

        digitButtons.forEach { (button, digit) ->
            stylePinButton(button)
            button.setOnClickListener { appendPinDigit(digit) }
        }

        stylePinButton(binding.pinKeyBackspace)
        binding.pinKeyBackspace.setOnClickListener { removePinDigit() }
    }

    private fun stylePinButton(button: MaterialButton) {
        button.backgroundTintList = ContextCompat.getColorStateList(this, R.color.kiosko_card)
        button.strokeColor = ContextCompat.getColorStateList(this, R.color.kiosko_card_stroke)
        button.strokeWidth = (2f * resources.displayMetrics.density).toInt()
    }

    private fun refreshState(forcePolicyApply: Boolean = false) {
        config = KioskConfigProvider.load(this)
        userProfiles = KioskUserStore.load(this, config)
        webLinks = KioskWebLinkStore.load(this)
        dropMissingWebLinkAssignments()
        reconcileUserState()

        effectivePolicyPackages = buildEffectivePolicyPackages()
        val policyConfig = config.copy(allowedPackages = effectivePolicyPackages)
        val currentSignature = PolicySignature(
            allowedPackages = policyConfig.allowedPackages,
            allowSystemUi = policyConfig.allowSystemUi,
            adminModeEnabled = policyConfig.adminModeEnabled,
        )
        val shouldApplyPolicy = forcePolicyApply || currentSignature != lastPolicySignature

        if (!policyController.isDeviceOwner()) {
            lastPolicySignature = null
            lastPolicyResult = PolicyApplyResult.DeviceOwnerMissing
        } else if (shouldApplyPolicy) {
            lastPolicyResult = policyController.apply(policyConfig)
            lastPolicySignature = currentSignature
        }

        val appCount = renderAllowedAppsForCurrentSession()

        renderStatus(lastPolicyResult)
        renderHeaderMeta()
        renderBottomInfo(appCount)
        renderAdminPanel(lastPolicyResult)
        renderSession()

        maybeEnterLockTask()
    }

    private fun reconcileUserState() {
        if (userProfiles.none { it.id == selectedAdminUserId }) {
            selectedAdminUserId = userProfiles.firstOrNull()?.id
        }
        if (userProfiles.none { it.id == currentUserId }) {
            currentUserId = null
        }

        if (!config.pinRequired && sessionState == SessionState.LOCKED) {
            sessionState = SessionState.USER
        }

        if (sessionState == SessionState.USER && currentUserId == null) {
            currentUserId = userProfiles.firstOrNull()?.id
            if (currentUserId == null && config.pinRequired) {
                sessionState = SessionState.LOCKED
            }
        }
    }

    private fun renderStatus(result: PolicyApplyResult) {
        val statusTextColor = when (result) {
            is PolicyApplyResult.Applied -> {
                binding.statusText.text = getString(R.string.status_device_owner_ok)
                R.color.kiosko_success
            }

            is PolicyApplyResult.DeviceOwnerMissing -> {
                binding.statusText.text = getString(R.string.status_device_owner_missing)
                R.color.kiosko_warning
            }

            is PolicyApplyResult.Failed -> {
                binding.statusText.text = result.message
                R.color.kiosko_error
            }
        }
        binding.statusText.setTextColor(ContextCompat.getColor(this, statusTextColor))

        binding.lockTaskStatusText.text = if (isInLockTaskMode()) {
            getString(R.string.status_lock_task_active)
        } else {
            getString(R.string.status_lock_task_inactive)
        }
        val lockTextColor = if (isInLockTaskMode()) R.color.kiosko_success else R.color.kiosko_muted
        binding.lockTaskStatusText.setTextColor(ContextCompat.getColor(this, lockTextColor))
    }

    private fun renderHeaderMeta() {
        val now = System.currentTimeMillis()
        val timeText = android.text.format.DateFormat.getTimeFormat(this).format(now)
        val dateText = android.text.format.DateFormat.format("EEE d MMM", now).toString()
        binding.dateTimeText.text = getString(R.string.kiosk_header_meta, timeText, dateText)
        val currentUserName = currentUserProfile()?.name
        binding.launcherUserNameText.text = if (sessionState == SessionState.USER && !currentUserName.isNullOrBlank()) {
            getString(R.string.launcher_user_header, currentUserName)
        } else {
            getString(R.string.kiosk_header_title)
        }
    }

    private fun renderBottomInfo(appCount: Int) {
        binding.bottomInfoText.text = getString(R.string.bottom_info, appCount)
    }

    private fun renderAllowedAppsForCurrentSession(): Int {
        val launcherAllowedPackages = when (sessionState) {
            SessionState.USER -> currentUserProfile()?.allowedPackages.orEmpty()
            SessionState.LOCKED, SessionState.ADMIN -> effectivePolicyPackages
        }

        val allowedApps = loadAllowedApps(launcherAllowedPackages)
        adapter.submitList(allowedApps)
        binding.emptyText.visibility = if (allowedApps.isEmpty()) View.VISIBLE else View.GONE
        return allowedApps.size
    }

    private fun renderAdminPanel(result: PolicyApplyResult) {
        binding.adminModeSourceText.text = if (config.adminModeEnabled) {
            getString(R.string.admin_mode_source_on)
        } else {
            getString(R.string.admin_mode_source_off)
        }
        binding.adminPolicyStatusText.text = when (result) {
            is PolicyApplyResult.Applied -> getString(R.string.status_device_owner_ok)
            is PolicyApplyResult.DeviceOwnerMissing -> getString(R.string.status_device_owner_missing)
            is PolicyApplyResult.Failed -> result.message
        }
        binding.adminLockStatusText.text = if (isInLockTaskMode()) {
            getString(R.string.status_lock_task_active)
        } else {
            getString(R.string.status_lock_task_inactive)
        }
        val packagesText = effectivePolicyPackages
            .toList()
            .sorted()
            .joinToString(separator = "\n")
            .ifBlank { getString(R.string.admin_no_allowed_packages) }
        binding.adminAllowedPackagesText.text = packagesText

        renderAdminUsersSpinner()
        renderAdminWebLinks()
        renderAdminUserPermissions()
    }

    private fun renderAdminUsersSpinner() {
        val labels = userProfiles.map { getString(R.string.admin_user_label, it.name, it.pin) }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        isUpdatingUserSpinner = true
        binding.adminUserSpinner.adapter = spinnerAdapter
        if (userProfiles.isNotEmpty()) {
            val index = userProfiles.indexOfFirst { it.id == selectedAdminUserId }.takeIf { it >= 0 } ?: 0
            binding.adminUserSpinner.setSelection(index, false)
            selectedAdminUserId = userProfiles[index].id
        }
        isUpdatingUserSpinner = false

        val hasUsers = userProfiles.isNotEmpty()
        binding.adminNoUsersText.visibility = if (hasUsers) View.GONE else View.VISIBLE
        binding.adminUserSpinner.visibility = if (hasUsers) View.VISIBLE else View.GONE
        binding.adminDeleteUserButton.isEnabled = userProfiles.size > 1
    }

    private fun renderAdminUserPermissions() {
        val selected = selectedAdminUserProfile()
        val assignableApps = loadInstalledLauncherApps()

        binding.adminNoAssignableAppsText.visibility =
            if (selected != null && assignableApps.isEmpty()) View.VISIBLE else View.GONE
        binding.adminUserAppsContainer.visibility =
            if (selected != null && assignableApps.isNotEmpty()) View.VISIBLE else View.GONE

        binding.adminUserAppsContainer.removeAllViews()
        if (selected == null) return

        assignableApps.forEachIndexed { index, app ->
            val row = buildAdminPermissionRow(selected.id, app)
            binding.adminUserAppsContainer.addView(row)
            if (index < assignableApps.lastIndex) {
                binding.adminUserAppsContainer.addView(
                    View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(1),
                        ).apply {
                            topMargin = dp(4)
                            bottomMargin = dp(4)
                        }
                        setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.kiosko_card_stroke))
                    },
                )
            }
        }
    }

    private fun buildAdminPermissionRow(userId: String, item: AllowedApp): View {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }

        val check = MaterialCheckBox(this).apply {
            text = item.label
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.kiosko_text))
            isChecked = selectedAdminUserProfile()?.allowedPackages?.contains(item.id) == true
            setOnCheckedChangeListener { _, isChecked ->
                updateUserPermission(userId, item.id, isChecked)
            }
        }

        val pkgText = TextView(this).apply {
            text = item.packageName
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.kiosko_muted))
            textSize = 12f
            setPadding(dp(36), 0, 0, 0)
        }

        wrapper.addView(check)
        wrapper.addView(pkgText)
        return wrapper
    }

    private fun updateUserPermission(userId: String, permissionKey: String, isAllowed: Boolean) {
        val index = userProfiles.indexOfFirst { it.id == userId }
        if (index < 0) return

        val existing = userProfiles[index]
        val updatedPackages = existing.allowedPackages.toMutableSet().apply {
            if (isAllowed) add(permissionKey) else remove(permissionKey)
        }

        userProfiles[index] = existing.copy(allowedPackages = updatedPackages)
        KioskUserStore.persist(this, userProfiles)

        if (sessionState == SessionState.USER && currentUserId == userId) {
            refreshState()
        }
    }

    private fun renderAdminWebLinks() {
        val sortedLinks = webLinks.sortedBy { it.name.lowercase() }
        val selected = selectedAdminUserProfile()

        binding.adminWebLinksContainer.removeAllViews()
        if (sortedLinks.isEmpty()) {
            binding.adminNoWebLinksText.text = getString(R.string.admin_no_web_links)
            binding.adminNoWebLinksText.visibility = View.VISIBLE
            binding.adminWebLinksContainer.visibility = View.GONE
            return
        }
        if (selected == null) {
            binding.adminNoWebLinksText.text = getString(R.string.admin_select_user_for_web_links)
            binding.adminNoWebLinksText.visibility = View.VISIBLE
            binding.adminWebLinksContainer.visibility = View.GONE
            return
        }

        binding.adminNoWebLinksText.text = getString(R.string.admin_no_web_links)
        binding.adminNoWebLinksText.visibility = View.GONE
        binding.adminWebLinksContainer.visibility = View.VISIBLE

        sortedLinks.forEachIndexed { index, link ->
            binding.adminWebLinksContainer.addView(
                buildAdminWebLinkRow(
                    userId = selected.id,
                    link = link,
                    isAssigned = link.permissionKey in selected.allowedPackages,
                ),
            )
            if (index < sortedLinks.lastIndex) {
                binding.adminWebLinksContainer.addView(
                    View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(1),
                        ).apply {
                            topMargin = dp(4)
                            bottomMargin = dp(4)
                        }
                        setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.kiosko_card_stroke))
                    },
                )
            }
        }
    }

    private fun buildAdminWebLinkRow(
        userId: String,
        link: KioskWebLink,
        isAssigned: Boolean,
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))

            val topRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }

            val check = MaterialCheckBox(this@MainActivity).apply {
                text = link.name
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.kiosko_text))
                isChecked = isAssigned
                setOnCheckedChangeListener { _, checked ->
                    updateUserPermission(userId, link.permissionKey, checked)
                }
            }

            val deleteButton = MaterialButton(
                this@MainActivity,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle,
            ).apply {
                text = getString(R.string.admin_delete_web_link)
                isAllCaps = false
                setOnClickListener { deleteWebLink(link.id) }
            }

            topRow.addView(
                check,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )
            topRow.addView(deleteButton)

            addView(topRow)
            addView(
                TextView(this@MainActivity).apply {
                    text = link.url
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.kiosko_muted))
                    textSize = 12f
                    setPadding(dp(36), 0, 0, 0)
                },
            )
        }
    }

    private fun deleteWebLink(linkId: String) {
        val index = webLinks.indexOfFirst { it.id == linkId }
        if (index < 0) return

        val permissionKey = webLinks[index].permissionKey
        webLinks.removeAt(index)
        KioskWebLinkStore.persist(this, webLinks)

        var usersChanged = false
        userProfiles = userProfiles.map { user ->
            if (permissionKey in user.allowedPackages) {
                usersChanged = true
                user.copy(allowedPackages = user.allowedPackages - permissionKey)
            } else {
                user
            }
        }.toMutableList()

        if (usersChanged) {
            KioskUserStore.persist(this, userProfiles)
        }

        Toast.makeText(this, R.string.admin_web_link_deleted, Toast.LENGTH_SHORT).show()
        refreshState()
    }

    private fun addWebLinkFromAdminInputs() {
        val name = binding.adminNewWebNameInput.text?.toString().orEmpty().trim()
        val normalizedUrl = KioskWebLink.normalizeUrl(
            binding.adminNewWebUrlInput.text?.toString().orEmpty(),
        )

        if (name.isBlank() || normalizedUrl == null) {
            Toast.makeText(this, R.string.admin_web_link_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        if (webLinks.any { it.url.equals(normalizedUrl, ignoreCase = true) }) {
            Toast.makeText(this, R.string.admin_web_link_exists, Toast.LENGTH_SHORT).show()
            return
        }

        webLinks.add(
            KioskWebLink(
                id = UUID.randomUUID().toString(),
                name = name,
                url = normalizedUrl,
            ),
        )
        KioskWebLinkStore.persist(this, webLinks)

        binding.adminNewWebNameInput.text?.clear()
        binding.adminNewWebUrlInput.text?.clear()
        Toast.makeText(this, R.string.admin_web_link_added, Toast.LENGTH_SHORT).show()
        refreshState()
    }

    private fun addUserFromAdminInputs() {
        val name = binding.adminNewUserNameInput.text?.toString().orEmpty().trim()
        val pin = binding.adminNewUserPinInput.text?.toString().orEmpty().filter { it.isDigit() }

        if (name.isBlank() || pin.length != 4) {
            Toast.makeText(this, R.string.admin_user_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        if (pin == config.adminPin || userProfiles.any { it.pin == pin }) {
            Toast.makeText(this, R.string.admin_user_pin_exists, Toast.LENGTH_SHORT).show()
            return
        }

        val created = KioskUserProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            pin = pin,
            allowedPackages = config.allowedPackages - packageName,
        )
        userProfiles.add(created)
        KioskUserStore.persist(this, userProfiles)
        selectedAdminUserId = created.id

        binding.adminNewUserNameInput.text?.clear()
        binding.adminNewUserPinInput.text?.clear()
        Toast.makeText(this, R.string.admin_user_added, Toast.LENGTH_SHORT).show()
        refreshState()
    }

    private fun deleteSelectedAdminUser() {
        if (userProfiles.size <= 1) {
            Toast.makeText(this, R.string.admin_user_delete_last_blocked, Toast.LENGTH_SHORT).show()
            return
        }

        val selected = selectedAdminUserProfile() ?: return
        userProfiles.removeAll { it.id == selected.id }
        if (currentUserId == selected.id) {
            currentUserId = null
            if (sessionState == SessionState.USER) {
                sessionState = if (config.pinRequired) SessionState.LOCKED else SessionState.USER
            }
        }

        KioskUserStore.persist(this, userProfiles)
        selectedAdminUserId = userProfiles.firstOrNull()?.id
        Toast.makeText(this, R.string.admin_user_deleted, Toast.LENGTH_SHORT).show()
        refreshState()
    }

    private fun renderSession() {
        when (sessionState) {
            SessionState.LOCKED -> {
                if (config.pinRequired) {
                    binding.pinLoginContainer.visibility = View.VISIBLE
                    binding.launcherContainer.visibility = View.GONE
                } else {
                    binding.pinLoginContainer.visibility = View.GONE
                    binding.launcherContainer.visibility = View.VISIBLE
                }
                binding.adminContainer.visibility = View.GONE
            }

            SessionState.USER -> {
                binding.pinLoginContainer.visibility = View.GONE
                binding.launcherContainer.visibility = View.VISIBLE
                binding.adminContainer.visibility = View.GONE
            }

            SessionState.ADMIN -> {
                binding.pinLoginContainer.visibility = View.GONE
                binding.launcherContainer.visibility = View.GONE
                binding.adminContainer.visibility = View.VISIBLE
            }
        }

        binding.userSwitchAccountButton.visibility = if (config.pinRequired) View.VISIBLE else View.GONE
    }

    private fun clearLoginIdentity() {
        binding.loginUsernameInput.text?.clear()
    }

    private fun appendPinDigit(digit: Int) {
        if (!config.pinRequired || sessionState != SessionState.LOCKED) return
        if (pinBuffer.length >= 4) return

        pinBuffer.append(digit)
        binding.pinErrorText.visibility = View.GONE
        updatePinDots(isError = false)

        if (pinBuffer.length == 4) {
            evaluatePin(pinBuffer.toString())
        }
    }

    private fun removePinDigit() {
        if (pinBuffer.isNotEmpty()) {
            pinBuffer.deleteCharAt(pinBuffer.lastIndex)
            binding.pinErrorText.visibility = View.GONE
            updatePinDots(isError = false)
        }
    }

    private fun evaluatePin(pin: String) {
        val enteredName = binding.loginUsernameInput.text?.toString().orEmpty().trim()
        if (enteredName.isBlank()) {
            Toast.makeText(this, R.string.login_username_required, Toast.LENGTH_SHORT).show()
            showPinError()
            return
        }

        when {
            enteredName.equals(ADMIN_USERNAME, ignoreCase = true) && pin == config.adminPin -> {
                resetPinState()
                clearLoginIdentity()
                sessionState = SessionState.ADMIN
                renderSession()
                refreshState()
            }

            else -> {
                val matchedUser = userProfiles.firstOrNull {
                    it.pin == pin && it.name.equals(enteredName, ignoreCase = true)
                }
                if (matchedUser != null) {
                    resetPinState()
                    clearLoginIdentity()
                    currentUserId = matchedUser.id
                    sessionState = SessionState.USER
                    renderSession()
                    refreshState()
                } else {
                    showPinError()
                }
            }
        }
    }

    private fun resetPinState() {
        pinBuffer.clear()
        binding.pinErrorText.visibility = View.GONE
        updatePinDots(isError = false)
    }

    private fun showPinError() {
        binding.pinErrorText.visibility = View.VISIBLE
        updatePinDots(isError = true)
        animatePinError()
        binding.pinDotsContainer.postDelayed({
            pinBuffer.clear()
            binding.pinErrorText.visibility = View.GONE
            updatePinDots(isError = false)
        }, 550L)
    }

    private fun animatePinError() {
        ObjectAnimator.ofFloat(
            binding.pinDotsContainer,
            View.TRANSLATION_X,
            0f,
            -18f,
            18f,
            -12f,
            12f,
            -6f,
            6f,
            0f,
        ).apply {
            duration = 420L
            start()
        }
    }

    private fun updatePinDots(isError: Boolean) {
        val dots = listOf(binding.pinDot1, binding.pinDot2, binding.pinDot3, binding.pinDot4)
        dots.forEachIndexed { index, dot ->
            val backgroundRes = when {
                isError -> R.drawable.bg_pin_dot_error
                index < pinBuffer.length -> R.drawable.bg_pin_dot_filled
                else -> R.drawable.bg_pin_dot_empty
            }
            dot.setBackgroundResource(backgroundRes)
        }
    }

    private fun maybeEnterLockTask() {
        if (sessionState == SessionState.ADMIN) return
        if (!policyController.isDeviceOwner()) return
        if (!policyController.isLockTaskPermittedForSelf()) return
        if (isInLockTaskMode()) return

        try {
            startLockTask()
        } catch (_: IllegalStateException) {
            // No-op: puede ocurrir si la actividad aun no esta en estado apto.
        }
    }

    private fun exitKioskCompletely() {
        try {
            if (isInLockTaskMode()) {
                stopLockTask()
            }
            policyController.releaseKioskForAdminExit()
            val settingsIntent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(settingsIntent)
            finishAffinity()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.admin_exit_kiosk_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun isInLockTaskMode(): Boolean {
        val am = getSystemService(ActivityManager::class.java)
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    private fun dropMissingWebLinkAssignments() {
        val validWebKeys = webLinks.map { it.permissionKey }.toSet()
        var changed = false
        val updatedUsers = userProfiles.map { user ->
            val filtered = user.allowedPackages.filter { key ->
                !KioskWebLink.isPermissionKey(key) || key in validWebKeys
            }.toSet()
            if (filtered.size != user.allowedPackages.size) {
                changed = true
                user.copy(allowedPackages = filtered)
            } else {
                user
            }
        }

        if (changed) {
            userProfiles = updatedUsers.toMutableList()
            KioskUserStore.persist(this, userProfiles)
        }
    }

    private fun buildEffectivePolicyPackages(): Set<String> {
        val userAssignedPackages = userProfiles
            .flatMap { it.allowedPackages }
            .filter { it.isNotBlank() && it != packageName && !KioskWebLink.isPermissionKey(it) }
            .toSet()
        return config.allowedPackages + userAssignedPackages + packageName
    }

    private fun currentUserProfile(): KioskUserProfile? =
        userProfiles.firstOrNull { it.id == currentUserId }

    private fun selectedAdminUserProfile(): KioskUserProfile? =
        userProfiles.firstOrNull { it.id == selectedAdminUserId }

    private fun loadAllowedApps(allowedPackages: Set<String>): List<AllowedApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = packageManager.queryIntentActivities(
            launcherIntent,
            PackageManager.MATCH_ALL,
        )

        val byPackage = linkedMapOf<String, android.content.pm.ResolveInfo>()
        for (info in resolveInfos) {
            byPackage.putIfAbsent(info.activityInfo.packageName, info)
        }

        val appItems = allowedPackages
            .asSequence()
            .filter { it != packageName && !KioskWebLink.isPermissionKey(it) }
            .mapNotNull { pkg ->
                val resolveInfo = byPackage[pkg] ?: return@mapNotNull null
                val label = resolveInfo.loadLabel(packageManager).toString().ifBlank { pkg }
                val icon = resolveInfo.loadIcon(packageManager)
                AllowedApp(
                    id = pkg,
                    label = label,
                    packageName = pkg,
                    icon = icon,
                )
            }
            .toList()

        val linksById = webLinks.associateBy { it.id }
        val webItems = allowedPackages
            .asSequence()
            .mapNotNull { KioskWebLink.idFromPermissionKey(it) }
            .mapNotNull { linksById[it] }
            .distinctBy { it.id }
            .map { link ->
                AllowedApp(
                    id = link.permissionKey,
                    label = link.name,
                    packageName = link.url,
                    icon = packageManager.defaultActivityIcon,
                    launchUrl = link.url,
                )
            }
            .toList()

        return (appItems + webItems).sortedBy { it.label.lowercase() }
    }

    private fun loadInstalledLauncherApps(): List<AllowedApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = packageManager.queryIntentActivities(
            launcherIntent,
            PackageManager.MATCH_ALL,
        )

        val byPackage = linkedMapOf<String, android.content.pm.ResolveInfo>()
        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            if (pkg == packageName) continue
            byPackage.putIfAbsent(pkg, info)
        }

        return byPackage
            .values
            .map { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName
                val label = resolveInfo.loadLabel(packageManager).toString().ifBlank { pkg }
                val icon = resolveInfo.loadIcon(packageManager)
                AllowedApp(
                    id = pkg,
                    label = label,
                    packageName = pkg,
                    icon = icon,
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun openAllowedApp(item: AllowedApp) {
        if (sessionState == SessionState.USER) {
            val user = currentUserProfile()
            if (user == null || item.id !in user.allowedPackages) {
                return
            }
        }

        if (item.launchUrl != null) {
            val webIntent = Intent(this, KioskWebActivity::class.java).apply {
                putExtra(KioskWebActivity.EXTRA_LINK_ID, item.id)
                putExtra(KioskWebActivity.EXTRA_LINK_LABEL, item.label)
                putExtra(KioskWebActivity.EXTRA_LINK_URL, item.launchUrl)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(webIntent)
            return
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(item.packageName)
        if (launchIntent == null) {
            Toast.makeText(this, R.string.app_not_installed, Toast.LENGTH_SHORT).show()
            return
        }

        if (sessionState == SessionState.USER && bringRunningTaskToFront(item.packageName)) {
            return
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
    }

    @Suppress("DEPRECATION")
    private fun bringRunningTaskToFront(targetPackage: String): Boolean {
        return runCatching {
            val am = getSystemService(ActivityManager::class.java)
            val task = am.getRunningTasks(50).firstOrNull { info ->
                info.topActivity?.packageName == targetPackage ||
                    info.baseActivity?.packageName == targetPackage
            } ?: return false
            am.moveTaskToFront(task.id, 0)
            true
        }.getOrElse {
            false
        }
    }

    private fun calculateSpanCount(): Int {
        val screenWidthDp = resources.configuration.screenWidthDp
        return when {
            screenWidthDp >= 1200 -> 6
            screenWidthDp >= 900 -> 5
            screenWidthDp >= 700 -> 4
            else -> 3
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
