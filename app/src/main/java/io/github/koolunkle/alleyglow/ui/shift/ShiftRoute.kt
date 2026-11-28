package io.github.koolunkle.alleyglow.ui.shift

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.koolunkle.alleyglow.R
import io.github.koolunkle.alleyglow.domain.model.AdSlot
import io.github.koolunkle.alleyglow.domain.model.CheckedInShop
import io.github.koolunkle.alleyglow.domain.model.MAX_BEFORE_AFTER_PHOTOS
import io.github.koolunkle.alleyglow.domain.model.currentAt
import io.github.koolunkle.alleyglow.domain.model.nextAfter
import io.github.koolunkle.alleyglow.domain.model.nightHour
import io.github.koolunkle.alleyglow.ui.common.LoadFailedNotice
import io.github.koolunkle.alleyglow.ui.common.PhotoRow
import io.github.koolunkle.alleyglow.ui.common.SectionCard
import io.github.koolunkle.alleyglow.ui.theme.AlleyGlowTheme
import io.github.koolunkle.alleyglow.ui.theme.Spacing
import io.github.koolunkle.alleyglow.ui.theme.TouchHeight
import kotlinx.coroutines.delay
import java.util.Calendar

@Composable
fun ShiftRoute(
    zoneId: String,
    zoneName: String,
    onCheckin: (shiftId: String, startedAt: Long) -> Unit,
    onFinish: (shiftId: String, startedAt: Long, beforePhotos: List<String>, bagsByHour: List<Int>) -> Unit,
    onLeave: () -> Unit,
    viewModel: ShiftViewModel = hiltViewModel(),
) {
    // 뒤로 가기 한 번에 봉투 기록·사진·위치 기록이 사라지지 않게 한 번 더 물음
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    BackHandler { confirmLeave = true }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.shift_leave_title)) },
            text = { Text(stringResource(R.string.shift_leave_body)) },
            // 대화상자를 먼저 닫아야 화면 전환 애니메이션 동안 남아 보이지 않음
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; onLeave() }) { Text(stringResource(R.string.shift_leave_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.shift_leave_cancel)) } },
        )
    }
    // 체크인 화면에서 돌아올 때마다 체크인 수를 새로 셈
    LifecycleResumeEffect(Unit) {
        viewModel.refreshCheckins()
        onPauseOrDispose {}
    }
    val checkedIn by viewModel.checkedIn.collectAsStateWithLifecycle()
    val bagLog by viewModel.bagLog.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val beforePhotos by viewModel.beforePhotos.collectAsStateWithLifecycle()
    val isTracking by viewModel.isTracking.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    val context = LocalContext.current
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    // 정확한 위치가 있어야 기록. 알림 권한은 없어도 기록은 되며 알림만 안 보임
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        permissionDenied = grants[Manifest.permission.ACCESS_FINE_LOCATION] != true
        if (!permissionDenied) viewModel.startTracking(zoneId)
    }
    ShiftScreen(
        zoneName = zoneName,
        uiState = uiState,
        startedAt = viewModel.startedAt,
        now = now,
        beforePhotos = beforePhotos,
        isTracking = isTracking,
        permissionDenied = permissionDenied,
        checkedIn = checkedIn,
        bagCount = bagLog.size,
        onAddBag = { viewModel.addBag(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) },
        onUndoBag = viewModel::undoBag,
        onRetry = viewModel::load,
        onStartTracking = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) {
                permissionDenied = false
                viewModel.startTracking(zoneId)
            } else {
                permissionLauncher.launch(
                    buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
                    }.toTypedArray(),
                )
            }
        },
        onAddBeforePhoto = viewModel::addBeforePhoto,
        onRemoveBeforePhoto = viewModel::removeBeforePhoto,
        onCheckin = { onCheckin(viewModel.shiftId, viewModel.startedAt) },
        onFinish = { onFinish(viewModel.shiftId, viewModel.startedAt, beforePhotos, viewModel.bagsByHour()) },
        // 전화 앱에 번호만 채워 열기(바로 걸지 않아 오작동 통화 방지, 전화 권한 불필요)
        onDial = { number -> context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) },
    )
}

@Composable
fun ShiftScreen(
    zoneName: String,
    uiState: ShiftUiState,
    startedAt: Long,
    now: Long,
    beforePhotos: List<String>,
    isTracking: Boolean,
    permissionDenied: Boolean,
    checkedIn: List<CheckedInShop>,
    bagCount: Int,
    onAddBag: () -> Unit,
    onUndoBag: () -> Unit,
    onRetry: () -> Unit,
    onStartTracking: () -> Unit,
    onAddBeforePhoto: (String) -> Unit,
    onRemoveBeforePhoto: (Int) -> Unit,
    onCheckin: () -> Unit,
    onFinish: () -> Unit,
    onDial: (number: String) -> Unit,
) {
    var showEmergency by rememberSaveable { mutableStateOf(false) }
    if (showEmergency) {
        EmergencyDialog(
            onDial = { number ->
                showEmergency = false
                onDial(number)
            },
            onDismiss = { showEmergency = false },
        )
    }
    Scaffold(
        // 근무 종료는 마지막에 꼭 거쳐야 하는 단계라 연두로 아래에 고정
        bottomBar = {
            Box(
                Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.l, vertical = Spacing.m),
            ) {
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(TouchHeight)) {
                    Text(stringResource(R.string.shift_finish), style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                StatusChip(isTracking)
                // 위급할 때 스크롤 없이 바로 보이도록 맨 위에 둠
                EmergencyChip(onClick = { showEmergency = true })
            }
            ShiftTimer(startedAt, now, zoneName)
            if (!isTracking) TrackingConsent(permissionDenied, onStartTracking)
            // 숫자와 그 숫자를 바꾸는 버튼을 한 카드에. 두 카드 높이를 맞춤
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                CounterCard(stringResource(R.string.shift_checkins), stringResource(R.string.shift_checkins_value, checkedIn.size)) {
                    Button(
                        onClick = onCheckin,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.surface,
                        ),
                        contentPadding = PaddingValues(horizontal = Spacing.s),
                        modifier = Modifier.fillMaxWidth().height(TouchHeight),
                    ) { Text(stringResource(R.string.shift_checkin), style = MaterialTheme.typography.labelLarge) }
                }
                CounterCard(stringResource(R.string.shift_bags), stringResource(R.string.shift_bags_value, bagCount)) {
                    BagButtons(bagCount, onAddBag, onUndoBag)
                }
            }
            if (checkedIn.isNotEmpty()) CheckinList(checkedIn)
            when (uiState) {
                ShiftUiState.Loading -> Box(Modifier.fillMaxWidth(), Alignment.Center) { CircularProgressIndicator() }
                ShiftUiState.Error -> LoadFailedNotice(stringResource(R.string.shift_slots_error), onRetry)
                is ShiftUiState.Success -> {
                    val hour = nightHour(now.hourOfDay())
                    SlotCard(uiState.slots.currentAt(hour), uiState.slots.nextAfter(hour))
                }
            }
            SectionCard(
                stringResource(R.string.shift_before_photos),
                required = false,
                hint = stringResource(R.string.shift_before_photos_caption),
            ) {
                PhotoRow(beforePhotos, MAX_BEFORE_AFTER_PHOTOS, stringResource(R.string.shift_before_photos), enabled = true, onAddBeforePhoto, onRemoveBeforePhoto)
            }
        }
    }
}

private fun Long.hourOfDay(): Int =
    Calendar.getInstance().apply { timeInMillis = this@hourOfDay }.get(Calendar.HOUR_OF_DAY)

@Preview
@Composable
private fun ShiftScreenPreview() {
    val now = System.currentTimeMillis()
    val hour = nightHour(now.hourOfDay())
    AlleyGlowTheme {
        ShiftScreen(
            zoneName = "이태원 관광특구 4",
            uiState = ShiftUiState.Success(
                listOf(
                    AdSlot("[가게명 1]", hour, hour + 1, "등판 조끼 · 수거통 옆면"),
                    AdSlot("[가게명 2]", hour + 1, hour + 2, "등판 조끼 · 수거통 옆면"),
                ),
            ),
            startedAt = now - 84 * 60_000,
            now = now,
            beforePhotos = emptyList(),
            isTracking = false,
            permissionDenied = false,
            checkedIn = listOf(CheckedInShop("[가게명 1]", now - 50 * 60_000), CheckedInShop("[가게명 2]", now - 10 * 60_000)),
            bagCount = 3,
            onAddBag = {}, onUndoBag = {},
            onRetry = {}, onStartTracking = {}, onAddBeforePhoto = {}, onRemoveBeforePhoto = {}, onCheckin = {}, onFinish = {},
            onDial = {},
        )
    }
}
