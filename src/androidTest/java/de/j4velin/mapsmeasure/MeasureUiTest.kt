package de.j4velin.mapsmeasure

import android.app.Application
import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.core.content.edit
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.maps.model.LatLng
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The parts of the measure screen which work without the map, driven by a real view model
 */
@RunWith(AndroidJUnit4::class)
class MeasureUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext
        .applicationContext as Application

    private lateinit var viewModel: MeasureViewModel

    private val points = listOf(LatLng(0.0, 0.0), LatLng(0.0, 0.001), LatLng(0.001, 0.001))

    private fun string(id: Int) = app.getString(id)

    private fun viewModel(savedState: SavedStateHandle) =
        MeasureViewModel(savedState, PreferenceSettings(app), PlayServicesLocator(app), FileTraceStorage(app))

    @Before
    fun setUp() {
        clearSettings()
        viewModel = viewModel(SavedStateHandle())
        viewModel.setMetric(true)
    }

    @After
    fun clearSettings() {
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit(commit = true) { clear() }
    }

    private fun showValueBox() {
        composeRule.setContent {
            MapsMeasureTheme {
                val state by viewModel.uiState.collectAsState()
                var confirmDelete by remember { mutableStateOf(false) }
                ValueBox(
                    value = state.formattedValue(),
                    onToggleType = { viewModel.toggleType() },
                    onRemoveLast = { viewModel.removeLastPoint() },
                    onClearAll = { confirmDelete = true },
                )
                if (confirmDelete) {
                    DeleteAllDialog(
                        count = state.trace.size,
                        onConfirm = {
                            viewModel.clear()
                            confirmDelete = false
                        },
                        onDismiss = { confirmDelete = false },
                    )
                }
            }
        }
    }

    @Test
    fun valueShowsDistanceAndTapSwitchesToArea() {
        points.forEach { viewModel.addPoint(it) }
        showValueBox()
        val distance = viewModel.uiState.value.formattedValue()
        composeRule.onNodeWithText(distance).performClick()

        assertEquals(MeasureType.AREA, viewModel.uiState.value.type)
        val area = viewModel.uiState.value.formattedValue()
        assertTrue(area.endsWith("m²"))
        composeRule.onNodeWithText(area).assertExists()
    }

    @Test
    fun trashRemovesTheLastPoint() {
        points.forEach { viewModel.addPoint(it) }
        showValueBox()
        composeRule.onNodeWithContentDescription(string(R.string.delete)).performClick()

        assertEquals(points.dropLast(1), viewModel.uiState.value.trace)
        composeRule.onNodeWithText(viewModel.uiState.value.formattedValue()).assertExists()
    }

    @Test
    fun longPressAsksBeforeDeletingAll() {
        points.forEach { viewModel.addPoint(it) }
        showValueBox()
        composeRule.onNodeWithContentDescription(string(R.string.delete))
            .performTouchInput { longClick() }

        val question = app.resources.getQuantityString(R.plurals.delete_all, 3, 3)
        composeRule.onNodeWithText(question).assertExists()
        composeRule.onNodeWithText(string(android.R.string.no)).performClick()
        composeRule.onNodeWithText(question).assertDoesNotExist()
        assertEquals(3, viewModel.uiState.value.trace.size)

        composeRule.onNodeWithContentDescription(string(R.string.delete))
            .performTouchInput { longClick() }
        composeRule.onNodeWithText(string(android.R.string.yes)).performClick()
        assertTrue(viewModel.uiState.value.trace.isEmpty())
        composeRule.onNodeWithText(viewModel.uiState.value.formattedValue()).assertExists()
    }

    @Test
    fun drawerSelectsMeasureTypeAndMapType() {
        composeRule.setContent {
            MapsMeasureTheme {
                val state by viewModel.uiState.collectAsState()
                DrawerItems(
                    state = state,
                    onSearch = {},
                    onUnits = {},
                    onType = { viewModel.setType(it) },
                    onMapLayer = { viewModel.setMapLayer(it) },
                    onSave = {},
                    onMoreApps = {},
                    onAbout = {},
                )
            }
        }
        composeRule.onNodeWithText(string(R.string.measure_distance)).assertIsSelected()
        composeRule.onNodeWithText(string(R.string.measure_area)).performClick()
        composeRule.onNodeWithText(string(R.string.measure_area)).assertIsSelected()
        composeRule.onNodeWithText(string(R.string.measure_distance)).assertIsNotSelected()
        assertEquals(MeasureType.AREA, viewModel.uiState.value.type)

        composeRule.onNodeWithText(string(R.string.mapview_map)).assertIsSelected()
        composeRule.onNodeWithText(string(R.string.mapview_satellite)).performClick()
        composeRule.onNodeWithText(string(R.string.mapview_satellite)).assertIsSelected()
        assertEquals(MapLayer.SATELLITE, viewModel.uiState.value.mapLayer)
        // the map type is a setting, so it is remembered
        assertEquals(MapLayer.SATELLITE, viewModel(SavedStateHandle()).uiState.value.mapLayer)
    }

    @Test
    fun unitsDialogSwitchesUnits() {
        points.forEach { viewModel.addPoint(it) }
        composeRule.setContent {
            MapsMeasureTheme {
                val state by viewModel.uiState.collectAsState()
                UnitsDialog(
                    metric = state.metric,
                    distance = state.distance,
                    area = state.area,
                    onMetricChange = { viewModel.setMetric(it) },
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText(string(R.string.metric)).performClick()
        assertFalse(viewModel.uiState.value.metric)
        assertTrue(viewModel.uiState.value.formattedValue().endsWith("ft"))
        composeRule.onNodeWithText(string(R.string.metric)).performClick()
        assertTrue(viewModel.uiState.value.metric)
        // the setting is remembered
        assertTrue(viewModel(SavedStateHandle()).uiState.value.metric)
    }

    @Test
    fun saveDialogOffersOldFilesOnlyIfThereAreAny() {
        var hasOldFiles by mutableStateOf(false)
        var oldFilesClicked = false
        composeRule.setContent {
            MapsMeasureTheme {
                SaveDialog(
                    hasOldFiles = hasOldFiles,
                    onSave = {},
                    onLoad = {},
                    onShare = {},
                    onOldFiles = { oldFilesClicked = true },
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText(string(R.string.save)).assertExists()
        composeRule.onNodeWithText(string(R.string.load)).assertExists()
        composeRule.onNodeWithText(string(R.string.share)).assertExists()
        composeRule.onNodeWithText(string(R.string.old_files)).assertDoesNotExist()

        hasOldFiles = true
        composeRule.onNodeWithText(string(R.string.old_files)).performClick()
        assertTrue(oldFilesClicked)
    }

    @Test
    fun stateSurvivesRecreation() {
        val savedState = SavedStateHandle()
        viewModel(savedState).apply {
            points.forEach { addPoint(it) }
            setType(MeasureType.AREA)
            assertTrue(consumeFirstStart())
        }

        // what the system hands a new view model after a rotation or process death
        val restored = viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))
        assertEquals(points, restored.uiState.value.trace)
        assertEquals(MeasureType.AREA, restored.uiState.value.type)
        assertFalse(restored.consumeFirstStart())
    }
}
