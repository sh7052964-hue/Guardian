package com.guardian360.applications

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Games
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.PastelAmber
import com.example.ui.theme.PastelBlue
import com.example.ui.theme.PastelIndigo
import com.example.ui.theme.PastelMint
import com.example.ui.theme.PastelPurple
import com.example.ui.theme.PastelRose
import com.example.ui.theme.RoyalCobaltStart
import com.example.ui.theme.SapphireTrust
import com.example.ui.theme.SecureGreen
import com.guardian360.common.AgeRating
import com.guardian360.common.EmptyStatePanel
import com.guardian360.common.GuardianCard
import com.guardian360.common.SectionHeader
import com.guardian360.common.StatusPill
import com.guardian360.database.CachedAppInventoryEntity
import com.guardian360.parent.ParentOverviewBundle
import com.guardian360.parent.ParentViewModel
import com.guardian360.policies.AppCategory
import com.guardian360.policies.GameSubcategory
import com.guardian360.website.GuardianDnsFilterVpnService

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppsCategoriesScreen(
    bundle: ParentOverviewBundle,
    selectedChildUid: String?,
    viewModel: ParentViewModel,
    onOpenYouTubeControls: () -> Unit = {},
    onOpenReports: () -> Unit = {}
) {
    val context = LocalContext.current
    val device = bundle.devices.firstOrNull { it.childUid == selectedChildUid } ?: bundle.devices.firstOrNull()
    val policy = bundle.policies.firstOrNull { it.childUid == device?.childUid }

    if (device == null || policy == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            EmptyStatePanel(
                title = "No Supervised Device Selected",
                message = "Pair a child device to manage app categories, game subcategories, age ratings, and individual app exceptions."
            )
        }
        return
    }

    var blockedCategories by remember(policy.childUid, policy.policyVersion) { mutableStateOf(policy.blockedCategories.toSet()) }
    var blockedPackages by remember(policy.childUid, policy.policyVersion) { mutableStateOf(policy.blockedPackages.toSet()) }
    var allowedPackages by remember(policy.childUid, policy.policyVersion) { mutableStateOf(policy.allowedPackages.toSet()) }
    var maxAgeRating by remember(policy.childUid, policy.policyVersion) { mutableStateOf(AgeRating.fromString(policy.maxAgeRating)) }
    var selectedPackages by remember { mutableStateOf(emptySet<String>()) }
    var activeCategoryFilter by remember { mutableStateOf<AppCategory?>(null) }
    var activeTab by remember { mutableIntStateOf(0) } // 0=Categories, 1=All Apps, 2=Blocked, 3=Allowed
    var searchQuery by remember { mutableStateOf("") }
    var detailApp by remember { mutableStateOf<CachedAppInventoryEntity?>(null) }

    fun isAppBlocked(app: CachedAppInventoryEntity): Boolean {
        return app.packageName in blockedPackages ||
            (app.category in blockedCategories && app.packageName !in allowedPackages)
    }

    val filteredApps = bundle.localInventory.filter { app ->
        val matchesCategory = activeCategoryFilter == null || app.category == activeCategoryFilter?.name
        val matchesSearch = searchQuery.isBlank() ||
            app.appLabel.contains(searchQuery, ignoreCase = true) ||
            app.packageName.contains(searchQuery, ignoreCase = true)
        val blocked = isAppBlocked(app)
        val matchesTab = when (activeTab) {
            2 -> blocked
            3 -> !blocked
            else -> true
        }
        matchesCategory && matchesSearch && matchesTab
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(
                title = "App Management",
                subtitle = "Control installed apps, Play ratings & category exceptions.",
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.refreshLocalDeviceInventory() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Installed Apps")
            }
        }

        // Top Tabs matching PDF Page 4 (Categories / All Apps / Blocked / Allowed)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Categories", "All Apps", "Blocked", "Allowed").forEachIndexed { idx, label ->
                FilterChip(
                    selected = activeTab == idx,
                    onClick = { activeTab = idx },
                    label = { Text(label) }
                )
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text("Search installed apps...") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().testTag("app_search_input")
        )

        // Age Rating Filter
        GuardianCard {
            Text("Maximum Allowed Age Rating", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AgeRating.entries.forEach { rating ->
                    FilterChip(
                        selected = maxAgeRating == rating,
                        onClick = { maxAgeRating = rating },
                        label = { Text(rating.label) }
                    )
                }
            }
        }

        // Featured Pastel Category Tiles matching PDF Page 4 when Categories tab active
        if (activeTab == 0) {
            val featured = listOf(
                Triple(AppCategory.EDUCATIONAL, PastelBlue, Icons.Rounded.School),
                Triple(AppCategory.GAMES, PastelMint, Icons.Rounded.Games),
                Triple(AppCategory.SOCIAL_MEDIA, PastelIndigo, Icons.Rounded.Apps),
                Triple(AppCategory.VIDEO_STREAMING, PastelRose, Icons.Rounded.PlayCircle),
                Triple(AppCategory.SHOPPING, PastelAmber, Icons.Rounded.ShoppingCart),
                Triple(AppCategory.PRODUCTIVITY, PastelPurple, Icons.Rounded.SmartToy)
            )
            featured.chunked(2).forEach { pair ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { (cat, bg, icon) ->
                        val count = bundle.localInventory.count { it.category == cat.name }
                        val catBlocked = cat.name in blockedCategories
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    activeCategoryFilter = if (activeCategoryFilter == cat) null else cat
                                    activeTab = 1
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = if (catBlocked) PastelRose else bg.copy(alpha = 0.72f),
                            border = BorderStroke(
                                1.dp,
                                if (catBlocked) DangerCrimson.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = cat.displayName,
                                    tint = if (catBlocked) DangerCrimson else SapphireTrust,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(cat.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(
                                        text = if (catBlocked) "$count apps • Blocked" else "$count apps",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (catBlocked) DangerCrimson else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Full 21 Categories Chip Selector + Category Block Switch
        GuardianCard {
            Text("All App Categories (21)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = activeCategoryFilter == null,
                    onClick = { activeCategoryFilter = null },
                    label = { Text("All (${bundle.localInventory.size})") }
                )
                AppCategory.entries.forEach { cat ->
                    val isBlockedCat = cat.name in blockedCategories
                    FilterChip(
                        selected = activeCategoryFilter == cat,
                        onClick = { activeCategoryFilter = if (activeCategoryFilter == cat) null else cat },
                        label = {
                            Text(if (isBlockedCat) "Blocked: ${cat.displayName}" else cat.displayName)
                        }
                    )
                }
            }
            if (activeCategoryFilter != null) {
                Spacer(Modifier.height(10.dp))
                val targetCat = activeCategoryFilter!!
                val catBlocked = targetCat.name in blockedCategories
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Block ${targetCat.displayName}", style = MaterialTheme.typography.titleSmall)
                    Switch(
                        checked = catBlocked,
                        onCheckedChange = { checked ->
                            blockedCategories = if (checked) blockedCategories + targetCat.name else blockedCategories - targetCat.name
                        }
                    )
                }
                if (targetCat == AppCategory.GAMES) {
                    Spacer(Modifier.height(6.dp))
                    Text("Games Subcategories (15):", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GameSubcategory.entries.forEach { sub ->
                            StatusPill(text = sub.displayName, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }

        // Installed Apps List with Allow / Block / Details pills (Matching PDF Page 4 & Page 7)
        GuardianCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Installed Apps (${filteredApps.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { selectedPackages = filteredApps.map { it.packageName }.toSet() }) {
                        Text("All")
                    }
                    OutlinedButton(onClick = { selectedPackages = emptySet() }) {
                        Text("Clear")
                    }
                }
            }
            if (selectedPackages.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            blockedPackages = blockedPackages + selectedPackages
                            allowedPackages = allowedPackages - selectedPackages
                            selectedPackages = emptySet()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Block (${selectedPackages.size})") }
                    OutlinedButton(
                        onClick = {
                            allowedPackages = allowedPackages + selectedPackages
                            blockedPackages = blockedPackages - selectedPackages
                            selectedPackages = emptySet()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Allow Selected") }
                }
            }
            Spacer(Modifier.height(8.dp))
            filteredApps.take(40).forEach { app ->
                val isBlocked = isAppBlocked(app)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { detailApp = app }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = app.packageName in selectedPackages,
                        onCheckedChange = { checked ->
                            selectedPackages = if (checked) selectedPackages + app.packageName else selectedPackages - app.packageName
                        }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.appLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${AppCategory.fromString(app.category).displayName} • ${AgeRating.fromString(app.ageRating).label} • ${app.minutesToday}m",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { detailApp = app },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBlocked) DangerCrimson else SecureGreen
                        ),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(if (isBlocked) "Blocked" else "Allow", maxLines = 1)
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onOpenReports,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Rounded.Assessment, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("View App Usage Report")
        }

        Button(
            onClick = {
                val candidate = policy.copy(
                    maxAgeRating = maxAgeRating.name,
                    blockedCategories = blockedCategories.toList(),
                    blockedPackages = blockedPackages.toList(),
                    allowedPackages = allowedPackages.toList()
                )
                viewModel.simulatePolicyChange(
                    candidate = candidate,
                    device = device,
                    apps = bundle.localInventory,
                    hasLocationPermission = false,
                    isVpnPrepared = GuardianDnsFilterVpnService.prepareIntent(context) == null
                )
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("simulate_apps_policy_btn")
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Simulate & Save App Policy")
        }
    }

    // App Details Dialog matching PDF Page 4 Right
    val currentDetail = detailApp
    if (currentDetail != null) {
        val blockedNow = isAppBlocked(currentDetail)
        AlertDialog(
            onDismissRequest = { detailApp = null },
            title = {
                Column {
                    Text(currentDetail.appLabel, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${AppCategory.fromString(currentDetail.category).displayName} • ${AgeRating.fromString(currentDetail.ageRating).label} • ${currentDetail.minutesToday}m today",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = PastelMint,
                            border = BorderStroke(1.dp, if (!blockedNow) SecureGreen else Color.Transparent),
                            modifier = Modifier.weight(1f).clickable {
                                allowedPackages = allowedPackages + currentDetail.packageName
                                blockedPackages = blockedPackages - currentDetail.packageName
                                detailApp = null
                            }
                        ) {
                            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = SecureGreen)
                                Spacer(Modifier.height(4.dp))
                                Text("Allow", style = MaterialTheme.typography.labelLarge, color = Color(0xFF064E3B))
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = PastelRose,
                            border = BorderStroke(1.dp, if (blockedNow) DangerCrimson else Color.Transparent),
                            modifier = Modifier.weight(1f).clickable {
                                blockedPackages = blockedPackages + currentDetail.packageName
                                allowedPackages = allowedPackages - currentDetail.packageName
                                detailApp = null
                            }
                        ) {
                            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.Block, contentDescription = null, tint = DangerCrimson)
                                Spacer(Modifier.height(4.dp))
                                Text("Block", style = MaterialTheme.typography.labelLarge, color = DangerCrimson)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = PastelAmber,
                            modifier = Modifier.weight(1f).clickable {
                                detailApp = null
                                if (currentDetail.packageName.contains("youtube", ignoreCase = true)) {
                                    onOpenYouTubeControls()
                                }
                            }
                        ) {
                            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = AlertAmber)
                                Spacer(Modifier.height(4.dp))
                                Text("Limits", style = MaterialTheme.typography.labelLarge, color = Color(0xFF78350F))
                            }
                        }
                    }

                    if (currentDetail.packageName.contains("youtube", ignoreCase = true)) {
                        OutlinedButton(
                            onClick = {
                                detailApp = null
                                onOpenYouTubeControls()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = DangerCrimson)
                            Spacer(Modifier.width(8.dp))
                            Text("Open YouTube Shorts & Safety Controls")
                        }
                    }

                    Text(
                        text = "Package: ${currentDetail.packageName}\nWeekly usage: ${currentDetail.minutesWeek}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { detailApp = null }) { Text("Done") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GooglePlayControlsScreen(
    bundle: ParentOverviewBundle,
    selectedChildUid: String?,
    viewModel: ParentViewModel
) {
    val context = LocalContext.current
    val device = bundle.devices.firstOrNull { it.childUid == selectedChildUid } ?: bundle.devices.firstOrNull()
    val policy = bundle.policies.firstOrNull { it.childUid == device?.childUid }

    if (device == null || policy == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            EmptyStatePanel(title = "No Child Device", message = "Pair a child device to configure Managed Google Play restrictions.")
        }
        return
    }

    var installApproval by remember(policy.policyVersion) { mutableStateOf(policy.playStoreApprovalRequired) }
    var iapRestricted by remember(policy.policyVersion) { mutableStateOf(policy.iapRestricted) }
    var paidRestricted by remember(policy.policyVersion) { mutableStateOf(policy.paidAppsRestricted) }
    var unknownBlocked by remember(policy.policyVersion) { mutableStateOf(policy.unknownSourcesBlocked) }
    var maxRating by remember(policy.policyVersion) { mutableStateOf(AgeRating.fromString(policy.maxAgeRating)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(
            title = "Google Play Store Management",
            subtitle = "Managed Google Play & Android Enterprise app distribution controls."
        )

        GuardianCard {
            val isDo = viewModel.enrollmentStatus.value.isDeviceOwner
            StatusPill(
                text = if (isDo) "Device Owner Managed Play Active" else "Requires Device Owner for OS-level Play Lock",
                color = if (isDo) EmeraldShield else MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.height(12.dp))
            ToggleRow("App Installation Approval", "Require Parent confirmation for newly detected/installed apps", installApproval) {
                installApproval = it
            }
            ToggleRow("In-App Purchase Restrictions", "Send Managed Play IAP restriction bundle", iapRestricted) {
                iapRestricted = it
            }
            ToggleRow("Paid App Restrictions", "Restrict paid Play Store downloads", paidRestricted) {
                paidRestricted = it
            }
            ToggleRow("Block Unknown Sources (Sideloading)", "Enforce DISALLOW_INSTALL_UNKNOWN_SOURCES when Device Owner", unknownBlocked) {
                unknownBlocked = it
            }
            Spacer(Modifier.height(10.dp))
            Text("Store Age Rating Ceiling", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AgeRating.entries.forEach { rating ->
                    FilterChip(selected = maxRating == rating, onClick = { maxRating = rating }, label = { Text(rating.label) })
                }
            }
        }

        Button(
            onClick = {
                val candidate = policy.copy(
                    playStoreApprovalRequired = installApproval,
                    iapRestricted = iapRestricted,
                    paidAppsRestricted = paidRestricted,
                    unknownSourcesBlocked = unknownBlocked,
                    maxAgeRating = maxRating.name
                )
                viewModel.simulatePolicyChange(
                    candidate,
                    device,
                    bundle.localInventory,
                    false,
                    GuardianDnsFilterVpnService.prepareIntent(context) == null
                )
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Simulate & Save Google Play Controls")
        }
    }
}

@Composable
internal fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
