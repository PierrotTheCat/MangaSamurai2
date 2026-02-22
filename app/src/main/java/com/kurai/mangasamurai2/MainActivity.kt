package com.kurai.mangasamurai2

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Html
import android.text.InputType
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.WindowCompat
import androidx.core.widget.NestedScrollView
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LifecycleObserver
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.github.pedrovgs.deeppanel.DeepPanel
import com.github.pedrovgs.deeppanel.Panel
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.MaterialColors
import com.kurai.mangasamurai2.databinding.ActivityMainBinding
import com.kurai.mangasamurai2.ui.home.HomeFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import okhttp3.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Timer
import java.util.TimerTask
import android.os.Bundle as nBundle
import java.util.zip.ZipInputStream
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext




class MainActivity : AppCompatActivity(), LifecycleObserver {


    private var colorize: Boolean = false
    private lateinit var binding: ActivityMainBinding
    val i = 0
    var indexPanel: Int = -1
    var indexPage: Int = 0
    private val pageList = ArrayList<DocumentFile>()
    val panelList = ArrayList<Bitmap>()
    private var selectedFolderUri: Uri? = null
    private lateinit var progressBar: ProgressBar
    private lateinit var textView: TextView
    private lateinit var adView: AdView
    var color: Int = Color.argb(120, 120, 120, 120)
    private var isZoomed = false // Flag, um zwischen normaler Ansicht und Zoom zu wechseln
    private var matrix = Matrix()
    private var savedMatrix = Matrix()
    private var start = PointF()
    private var dragMode = false
    private var scaleFactor = 1.0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var dX = 0f
    private var dY = 0f
    private val longClickDuration = 1200L
    private var isLongPress = false
    private var longPressHandler: Handler? = null
    private val zoomHandler = Handler(Looper.getMainLooper())
    private var pendingZoomRunnable: Runnable? = null
    private var targetScaleFactor = 1.0f
    private var isDragging = false
    private val REQUEST_CODE = 100
    private var isPanelSwitchingActive = false
    private var panelSwitchTimer: Timer? = null
    private var panelSwitchInterval: Long = 10000L
    private lateinit var progressBarPanel: ProgressBar
    private var progressTask: TimerTask? = null
    private var appOpenAd: AppOpenAd? = null
    private var timerMultiplier: Float = 1.0f // Standardwert

    // Variable, um die Toolbar-Sichtbarkeit zu speichern
    private var isToolbarVisible = true

    // Variable, um Swipe und Drag zu trennen
    private var isSwiping = false
    private var isReadingRightToLeft = true // Standardrichtung von links nach rechts
    private var documentFile: DocumentFile? = null
    private var showedHint = false
    private var startTime: Long = 0
    private var pausedProgress = 0.0 // Speichert den aktuellen Fortschritt
    private var isPaused = false // Status-Flag
    private var remainingTime = 0L // Speichert verbleibende Zeit
    private var isFullPageView = false
    private val fullPageList = ArrayList<Bitmap>() // Speichert vollständige Seiten

    private val handler = Handler(Looper.getMainLooper())
    private var progressTaskRunnable: Runnable? = null

    private enum class ZoomState { ZOOMED_IN, FULL_PAGE, DEFAULT }

    private var currentZoomState = ZoomState.DEFAULT

    private lateinit var nestedScrollView: NestedScrollView
    private var isWebtoonMode = false
    private lateinit var toolbar: Toolbar
    private var currentProgressAnimator: ObjectAnimator? = null
    private var currentScale = 1f

    private var lastX = 0f
    private var lastY = 0f

    private val touchSlop by lazy {
        ViewConfiguration.get(this).scaledTouchSlop
    }


    private fun displayImage(uri: Uri) {
        Log.d("MainActivity", "Bild ausgewählt: $uri")
        // Hier kannst du dein vorhandenes Panel-System aufrufen
    }

    private fun getFileName(uri: Uri): String {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex("_display_name")
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        } ?: uri.lastPathSegment ?: "unknown"
    }

    private val PICK_FOLDER_REQUEST_CODE = 123
    private var folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d("folderpickerlauncher", "true")
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                data?.data?.also { uri ->
                    // ProgressBar anzeigen
                    progressBar.visibility = View.VISIBLE
                    documentFile = DocumentFile.fromTreeUri(this, uri)


                    // Coroutine starten, um den Schneidevorgang im Hintergrund auszuführen
                    CoroutineScope(Dispatchers.Default).launch {
                        // Hier den Schneidevorgang durchführen
                        selectedFolderUri = uri
                        //panelList.clear()
                        //listFilesInFolder(selectedFolderUri!!)
                        if (documentFile != null && documentFile!!.isDirectory) {
                            processChapters(documentFile!!)
                        }


                        // ProgressBar auf dem Haupt-UI-Thread ausblenden
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            indexPanel = 0
                            binding.imageView2.setImageBitmap(panelList[indexPanel % panelList.size])
                            binding.imageView2.visibility = View.VISIBLE
                            textView.visibility = View.INVISIBLE
                            // Hinweis anzeigen
                            if (!showedHint) {
                                showedHint = true
                                showHintDialog()
                            }

                            //progressBar.visibility = View.GONE
                            /*val instructionText = "Tap to start. Controls: <br><br>" +
                                "<b>Tap</b>: Move to the next panel.<br>" +
                                "<b>Long Press</b>: Go back to the previous panel.<br>" +
                                "<b>Double Tap</b>: Zoom in or out on the current panel.<br>" +
                                "<b>Drag</b>: Pan around a zoomed panel.<br>" +
                                "<b>Hide Taskbar</b>: Fling panel upwards/downwards to hide/show the taskbar<br>" +
                                "<b>Auto Mode</b>: Enable in the menu to automatically navigate through panels.<br><br>" +
                                "Enjoy your reading experience. \uD83C\uDF38\uD83D\uDDE1\uFE0F"
                        textView.text = Html.fromHtml(instructionText, Html.FROM_HTML_MODE_LEGACY)*/


                            /*val size = panelList.size
                        indexPanel--
                        imageView.setOnClickListener {
                            indexPanel++
                            imageView.setImageBitmap(panelList[indexPanel%size])
                            Log.d("imageViewListener", (indexPanel%size).toString())
                            textView.visibility = View.GONE
                        }
                        imageView.setOnLongClickListener{
                            if(indexPanel>0) {
                                indexPanel--
                            } else {
                                val message = "Erste Seite erreicht."
                                val toast = Toast.makeText(applicationContext, message, Toast.LENGTH_LONG)
                                toast.show()
                            }
                            imageView.setImageBitmap(panelList[indexPanel%size])
                            Log.d("imageViewListener", (indexPanel%size).toString())
                            true
                        }*/
                        }


                    }


                }
            } else {
                // Handle case when user cancels folder selection
            }
        }

    override fun onCreate(savedInstanceState: nBundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialisiere das Mobile Ads SDK
        MobileAds.initialize(this) {}

        textView = findViewById(R.id.text_home)
        textView.visibility = View.VISIBLE

        toolbar = findViewById<Toolbar>(R.id.myToolbar)
        /*val color = MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorPrimaryContainer,
            "#C0C0C0".toColorInt())
        val translucentColor = ColorUtils.setAlphaComponent(color, (0.65f * 255).toInt()) // 128 = 50%
        toolbar.setBackgroundColor(translucentColor)*/

        val baseColor = MaterialColors.getColor(
            toolbar,
            com.google.android.material.R.attr.colorPrimaryInverse,
            Color.DKGRAY
        )

        // Transparente Varianten erzeugen
        val startColor =
            ColorUtils.setAlphaComponent(baseColor, (1.0f * 255).toInt())  // 65% Deckkraft
        val endColor = ColorUtils.setAlphaComponent(baseColor, 0)  // Voll transparent

        // GradientDrawable erzeugen
        val gradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(startColor, endColor)
        )

        toolbar.background = gradientDrawable


        setSupportActionBar(toolbar)

        binding.imageView2.bringToFront()

        val isDarkTheme = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        WindowCompat.setDecorFitsSystemWindows(window, true)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)

        // Statusleiste
        insetsController.isAppearanceLightStatusBars = !isDarkTheme

        // Navigationsleiste
        insetsController.isAppearanceLightNavigationBars = !isDarkTheme


        //window.statusBarColor = ContextCompat.getColor(this, R.color.status_bar_dark)


        // Initialisiere die ProgressBar
        progressBar = findViewById(R.id.progressBar)

        // Setze die Sichtbarkeit der ProgressBar auf GONE, um sie zunächst unsichtbar zu machen
        progressBar.visibility = View.GONE

        // ProgressBar initialisieren
        progressBarPanel = findViewById(R.id.progressBarPanel)
        fadeOutProgressBar()

        DeepPanel.initialize(this)

        //Log.d("onviewcreated", "before")
        //openFolderPicker()
        //Log.d("onviewcreated", "after")


// Initialisiere GestureDetector für DoubleTap und SingleTap
        val gestureDetector =
            GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: MotionEvent) {
                    if (!isZoomed && !isDragging) {
                        /*if (indexPanel>0) indexPanel--
                    //imageView.setImageBitmap(panelList[indexPanel % panelList.size])
                    Handler(Looper.getMainLooper()).postDelayed({
                    crossfadePanel(panelList[indexPanel], binding.imageView2)
                    }, 300)*/
                        binding.imageView2.scaleX = 1f
                        binding.imageView2.scaleY = 1f
                        binding.imageView2.translationX = 0f
                        binding.imageView2.translationY = 0f
                        currentScale = 1f
                        onManualPanelBack()
                        textView.visibility = View.GONE
                    }

                    super.onLongPress(e)
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    currentZoomState = when (currentZoomState) {
                        ZoomState.DEFAULT -> {
                            // Zoom auf 2.0f
                            binding.imageView2.scaleX = 2.0f
                            binding.imageView2.scaleY = 2.0f
                            ZoomState.ZOOMED_IN
                        }

                        ZoomState.ZOOMED_IN -> {
                            // Wechsel zur FullPage-Ansicht
                            switchToFullPageView()
                            ZoomState.FULL_PAGE
                        }

                        ZoomState.FULL_PAGE -> {
                            // Zurück auf 1.0f
                            binding.imageView2.scaleX = 1.0f
                            binding.imageView2.scaleY = 1.0f
                            ZoomState.DEFAULT
                        }
                    }
                    return true
                }


                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    longPressHandler?.removeCallbacksAndMessages(null) // Stoppe den Long-Press Timer
                    isLongPress = false // Setze isLongPress zurück

                    if (!isDragging && e.eventTime - e.downTime < ViewConfiguration.getLongPressTimeout()) {
                        binding.imageView2.scaleX = 1f
                        binding.imageView2.scaleY = 1f
                        binding.imageView2.translationX = 0f
                        binding.imageView2.translationY = 0f
                        currentScale = 1f
                        onManualPanelSwitch() // Panel-Wechsel über die neue Methode
                    }

                    return true
                }

                /*override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                // Wischen nach oben/unten: Toolbar ein-/ausblenden
                if (kotlin.math.abs(distanceY) > kotlin.math.abs(distanceX)) {
                    // Vertikales Scrollen erkannt
                    if (distanceY > 0) {
                        // Wischen nach oben: Toolbar ausblenden
                        if (isToolbarVisible) toggleToolbar()
                    } else {
                        // Wischen nach unten: Toolbar einblenden
                        if (!isToolbarVisible) toggleToolbar()
                    }
                    return true
                }
                return false
            }*/

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    // Fling (schnelle vertikale Bewegung): Toolbar ein-/ausblenden
                    if (kotlin.math.abs(velocityY) > kotlin.math.abs(velocityX) && kotlin.math.abs(
                            velocityY
                        ) > 300
                    ) {
                        isSwiping = true // Swipe erkannt
                        if (velocityY > 0 && !isToolbarVisible) {
                            // Nach unten wischen: Toolbar einblenden
                            //toggleToolbar()
                            toggleToolbarAnimated()
                        } else if (velocityY < 0 && isToolbarVisible) {
                            // Nach oben wischen: Toolbar ausblenden
                            //toggleToolbar()
                            toggleToolbarAnimated()
                        }
                        // Nach dem Touch-Event Swipe zurücksetzen
                        if (e2.action == MotionEvent.ACTION_UP || e2.action == MotionEvent.ACTION_CANCEL) {
                            isSwiping = false
                        }
                        return true
                    }
                    return false
                }
            })

        // Pinch-Zooming
        val scaleGestureDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    binding.imageView2.pivotX = detector.focusX
                    binding.imageView2.pivotY = detector.focusY
                    isZoomed = true
                    return super.onScaleBegin(detector)
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    isZoomed = false
                    super.onScaleEnd(detector)
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {

                    val scaleFactor = detector.scaleFactor
                    var newScale = (currentScale * scaleFactor).coerceIn(0.5f, 3.0f)

                    // 🔹 Wenn kleiner als 0.8 → ganze Seite zeigen
                    if (newScale < 0.8f) {
                        newScale = 1f
                        binding.imageView2.translationX = 0f
                        binding.imageView2.translationY = 0f
                    }

                    // 🔹 Smooth Zoom (Lerp) für weniger Jitter
                    binding.imageView2.scaleX = lerp(binding.imageView2.scaleX, newScale, 0.3f)
                    binding.imageView2.scaleY = lerp(binding.imageView2.scaleY, newScale, 0.3f)

                    currentScale = newScale
                    return true
                }


            })

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        isDragging = false
        val dragThreshold =
            100 // Pixel, die das Panel über den sichtbaren Bereich hinaus bewegt werden kann


        // Setze den OnTouchListener für das ImageView
        binding.imageView2.setOnTouchListener { view, event ->
            // Wenn Pinch-to-Zoom aktiv ist, verarbeite nur das Zoomen
            gestureDetector.onTouchEvent(event) // Double Tap, Single Tap
            scaleGestureDetector.onTouchEvent(event)


            // 2️⃣ Drag für vergrößertes Panel
            handleDrag(event, view)

            // 3️⃣ LongPress & Panel-Swipe
            handleLongPressAndSwipe(event, view)

            /*
            when (event.actionMasked) {

                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
                    isDragging = currentScale > 1f
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {

                        val dx = event.rawX - lastX
                        val dy = event.rawY - lastY

                        binding.imageView2.translationX += dx
                        binding.imageView2.translationY += dy

                        lastX = event.rawX
                        lastY = event.rawY
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                }
            }


            if(!isZoomed) {
                val panelWidth = view.width
                val panelHeight = view.height
                val dragThreshold = Math.max(panelWidth, panelHeight) * 0.45f // Beispiel: 10% der Panelgröße

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                        dX = view.x - lastTouchX
                        dY = view.y - lastTouchY

                        isDragging = false
                        isLongPress = true // Setze isLongPress auf true
                        longPressHandler = Handler()
                        longPressHandler?.postDelayed({
                            if (isLongPress == true && isDragging == false) {
                                // Trigger Long-Press Aktion hier
                                if (indexPanel>0) indexPanel--
                                //imageView.setImageBitmap(panelList[indexPanel % panelList.size])
                                Handler(Looper.getMainLooper()).postDelayed({
                                    crossfadePanel(panelList[indexPanel], binding.imageView2)
                                }, 300)
                                textView.visibility = View.GONE
                            }
                        }, longClickDuration)
                    }

                    MotionEvent.ACTION_MOVE -> {
                        // Drag-and-Drop nur erlauben, wenn kein Swipe aktiv ist
                        if (!isSwiping) {
                            val deltaX = event.rawX - lastTouchX
                            val deltaY = event.rawY - lastTouchY

                            if (!isDragging && (Math.abs(deltaX) > touchSlop || Math.abs(deltaY) > touchSlop)) {
                                isDragging = true // Dragging erkannt

                            }

                            if (isDragging) {
                                // Berechne die neue Position
                                val newX = event.rawX + dX
                                val newY = event.rawY + dY

                                // Einschränkung der neuen Position
                                val viewWidth = view.width
                                val viewHeight = view.height
                                val parentWidth = (view.parent as View).width
                                val parentHeight = (view.parent as View).height

                                // Berechne den Sichtbereich unter Berücksichtigung der Schwellenwerte
                                val minX = -dragThreshold
                                val maxX = (parentWidth + dragThreshold - viewWidth).toFloat()
                                val minY = -dragThreshold
                                val maxY = (parentHeight + dragThreshold - viewHeight).toFloat()

                                // Begrenzen der X-Position
                                val constrainedX = when {
                                    newX < minX -> minX // links
                                    newX > maxX -> maxX // rechts
                                    else -> newX // innerhalb der Grenzen
                                }

                                // Begrenzen der Y-Position
                                val constrainedY = when {
                                    newY < minY -> minY // oben
                                    newY > maxY -> maxY // unten
                                    else -> newY // innerhalb der Grenzen
                                }

                                // Setze die neue Position des ImageView
                                view.animate()
                                    .x(constrainedX)
                                    .y(constrainedY)
                                    .setDuration(0)
                                    .start()
                            }

                            // Nach dem Touch-Event Swipe zurücksetzen
                            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                                isSwiping = false
                            }
                        }


                    }

                    MotionEvent.ACTION_UP -> {
                        view.isPressed = false
                        longPressHandler?.removeCallbacksAndMessages(null) // Stoppe den Long-Press Timer

                        isLongPress = false // Setze isLongPress zurück
                        isDragging = false
                        if (!isDragging) {
                            // Hier wird nur ein Klick behandelt
                            /* if (event.eventTime - event.downTime < ViewConfiguration.getLongPressTimeout()) {
                                         // Wechsel zum nächsten Panel
                                         imageView.scaleX = 1f
                                         imageView.scaleY = 1f
                                         imageView.translationX = 0f
                                         imageView.translationY = 0f
                                         indexPanel++
                                         imageView.setImageBitmap(panelList[indexPanel % panelList.size])
                                         textView.visibility = View.GONE
                                     }*/
                        }
                    }
                }
            }

 */
            true
        }


        // OnClick und OnLongClickListener wie zuvor
        /*imageView.setOnClickListener {
            indexPanel++
            imageView.setImageBitmap(panelList[indexPanel % panelList.size])
        }*/

        /*imageView.setOnLongClickListener {
            if (indexPanel > 0) indexPanel-- else Toast.makeText(this, "Erste Seite erreicht.", Toast.LENGTH_LONG).show()
            imageView.setImageBitmap(panelList[indexPanel % panelList.size])
            true
        }*/

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    private fun openFolderPicker() {

        Log.d("openfolderpicker", "true")
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        folderPickerLauncher.launch(intent)
    }

    fun processChapters(rootFolder: DocumentFile) {
        val subFolders = rootFolder.listFiles()
            .filter { it.isDirectory } // Unterordner filtern

        if (subFolders.isNotEmpty()) {
            // Es gibt Unterordner: Kapitel einzeln verarbeiten
            val chapters = subFolders.sortedBy { it.name }
            for (chapter in chapters) {
                Log.d("Chapter", "Kapitel: ${chapter.name}")
                processPages(chapter)
            }
        } else {
            // Keine Unterordner: Verarbeite den aktuellen Ordner als 1 Kapitel
            Log.d("Chapter", "Kapitel: ${rootFolder.name ?: "Unbenannt"}")
            processPages(rootFolder)
        }

    }

    fun processPages(folder: DocumentFile) {
        var currentMessage =
            "" // ganz oben in deiner Methode oder als Feld in der Klasse definieren

        val imageExtensions = listOf("jpg", "jpeg", "png", "webp")
        val pages = folder.listFiles()
            .filter {
                it.isFile && it.name?.substringAfterLast(".")?.lowercase() in imageExtensions
            }
            .sortedBy { it.name }

        val deepPanel = DeepPanel()
        fullPageList.clear()
        //panelList.clear() // auch Panels leeren, falls nötig

        if (pages.isNotEmpty()) {
            val katanaMessages = listOf(
                "\uD83C\uDF38",
                "\uD83C\uDF38\uD83C\uDF38",
                "\uD83C\uDF38\uD83C\uDF38\uD83C\uDF38",
                "\uD83C\uDF38\uD83C\uDF38\uD83C\uDF38\uD83C\uDF38",
                "\uD83C\uDF38\uD83C\uDF38\uD83C\uDF38\uD83C\uDF38\uD83C\uDF38"
            )

            val messageStep =
                if (pages.size < katanaMessages.size) 1 else pages.size / katanaMessages.size

            pages.forEachIndexed { index, page ->
                // 🪶 Statusmeldung abhängig vom Fortschritt
                val messageIndex = index / messageStep
                if (messageIndex < katanaMessages.size) {
                    val newMessage = katanaMessages[messageIndex]
                    if (newMessage != currentMessage) {
                        currentMessage = newMessage
                        runOnUiThread {
                            textView.apply {
                                alpha = 0f
                                text = newMessage
                                animate().alpha(1f).setDuration(500).start()
                                visibility = View.VISIBLE
                            }
                        }
                    }
                }

                Log.d("PageTracker", "Verarbeite Seite: Datei = ${page.name}")

                val inputStream = contentResolver.openInputStream(page.uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                val result = deepPanel.extractPanelsInfo(bitmap)
                val temp = result.panels.panelsInfo

                val sortedPanels = temp.sortedWith(compareBy<Panel> { it.top }
                    .thenBy { if (isReadingRightToLeft) -it.right else it.left })

                sortedPanels.forEach { panel ->
                    Log.d(
                        "DeepPanel", """Left: ${panel.left}, Top: ${panel.top}
                    |Right: ${panel.right}, Bottom: ${panel.bottom}
                    |Width: ${panel.width}, Height: ${panel.height}
                    """.trimMargin()
                    )

                    val panelBitmap = Bitmap.createBitmap(
                        bitmap,
                        panel.left,
                        panel.top,
                        panel.width,
                        panel.height
                    )
                    panelList.add(panelBitmap)
                    fullPageList.add(bitmap)
                }
            }
        } else {
            Log.d("Page", "Keine Bilddateien in diesem Ordner gefunden.")
        }
    }


    private fun listFilesInFolder(folderUri: Uri) {
        Log.d("listfilesinfolder", "true")

        val folder = DocumentFile.fromTreeUri(this, folderUri)
        if (folder != null && folder.isDirectory) {
            val files = folder.listFiles()
            if (files != null) {
                for (file in files) {
                    val fileName = file.name
                    val fileSize = file.length()
                    val fileUri = file.uri
                    //val treeUri: Uri = fileUri // dein tree URI hier
                    //val documentFile = DocumentFile.fromTreeUri(requireContext(), treeUri)

                    if (file != null) {
                        Log.d("File", "Name: $fileName, Size: $fileSize bytes")
                        if (fileName?.get(0)!!.isDigit()) {
                            pageList.add(file)
                        }
                    }
                }


            } else {
                Log.d("error", "files is null")
            }

            pageList.sortBy { it.name }
            // Zugriff auf pageList hier nach dem Hinzufügen der Dateien
            Log.d("pageListsize", pageList.size.toString())
            for (page in pageList) {
                page.name?.let { Log.d("pageList", it) }
            }


            val deepPanel = DeepPanel()

            for (page in pageList) {
                val bitmap = BitmapFactory.decodeStream(
                    this.contentResolver.openInputStream(page.uri)
                )

                val result = deepPanel.extractPanelsInfo(bitmap)
                val temp = result.panels.panelsInfo

                // Dynamische Sortierung basierend auf der Leserichtung
                val sortedPanels = if (isReadingRightToLeft) {
                    temp.sortedByDescending { it.right }.sortedBy { it.top } // Rechts nach links
                } else {
                    temp.sortedBy { it.left }.sortedBy { it.top } // Links nach rechts
                }

                // Panels der Liste hinzufügen
                sortedPanels.forEach { panel ->
                    Log.d(
                        "DeepPanel", """Left: ${panel.left}, Top: ${panel.top}
                |Right: ${panel.right}, Bottom: ${panel.bottom}
                |Width: ${panel.width}, Height: ${panel.height}
            """.trimMargin()
                    )
                    panelList.add(
                        Bitmap.createBitmap(
                            bitmap,
                            panel.left,
                            panel.top,
                            panel.width,
                            panel.height
                        )
                    )
                }
            }


        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d("onactivityresult", "true")
        if (requestCode == PICK_FOLDER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.also { uri ->
                val documentFile = DocumentFile.fromTreeUri(this, uri)
                if (documentFile != null && documentFile.isDirectory) {
                    processChapters(documentFile)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.options_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_option1 -> {
                // Aktion für Option 1
                progressBar.visibility = View.VISIBLE
                pageList.clear()
                panelList.clear()
                binding.imageView2.visibility = View.INVISIBLE
                openFolderPicker()
                //toggleToolbarAnimated()
                true
            }

            R.id.action_option2 -> {
                // Aktion für Option 2 - Zeige den Farbwähldialog an
                showColorPickerDialog()
                //toggleToolbarAnimated()
                true
            }

            R.id.menu_save_panel -> {
                saveCurrentPanel()
                //toggleToolbarAnimated()
                return true
            }

            R.id.action_save_panels -> {
                saveAllPanels()
                true
            }

            R.id.action_save_large_panels -> {
                saveLargePanelsOnly()
                true
            }

            R.id.action_set_multiplier -> {
                showMultiplierDialog()
                //toggleToolbarAnimated()
                true
            }

            R.id.action_auto_switch -> {
                // Aktion für "Automatischer Panel-Wechsel"
                togglePanelSwitching(item)
                //toggleToolbarAnimated()
                return true
            }

            R.id.action_toggle_reading_direction -> {
                toggleReadingDirection()
                //toggleToolbarAnimated()
                true
            }

            R.id.action_jump_to -> {
                showJumpToDialog()
                //toggleToolbarAnimated()
                return true
            }

            else -> super.onOptionsItemSelected(item)
        }


    }


    private fun saveCurrentPanel() {
        val bitmap = panelList[indexPanel % panelList.size] // Das aktuelle Panel

        val filename = "manga_panel_${System.currentTimeMillis()}.png"

        try {
            val outputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Scoped Storage für Android 10+ (API 29+)
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MangaPanels")
                }

                val uri =
                    contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let { contentResolver.openOutputStream(it) }
                    ?: throw IOException("Error occurred while creating file")
            } else {
                // Für ältere Android-Versionen (vor API 29)
                val directory = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "MangaPanels"
                )
                if (!directory.exists()) {
                    directory.mkdirs()
                }
                val file = File(directory, filename)
                FileOutputStream(file)
            }

            outputStream.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }

            Toast.makeText(this, "Panel successfully saved: $filename", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Error occurred while saving", Toast.LENGTH_SHORT).show()
        }
    }


    private fun showColorPickerDialog() {
        // Lade das Farbwähler-Layout
        val dialogView = LayoutInflater.from(this).inflate(R.layout.color_picker_dialog, null)

        val seekBarRed = dialogView.findViewById<SeekBar>(R.id.seekBarRed)
        val seekBarGreen = dialogView.findViewById<SeekBar>(R.id.seekBarGreen)
        val seekBarBlue = dialogView.findViewById<SeekBar>(R.id.seekBarBlue)
        val seekBarAlpha =
            dialogView.findViewById<SeekBar>(R.id.seekBarAlpha) // Neue SeekBar für Alpha
        val colorPreview = dialogView.findViewById<View>(R.id.colorPreview)

        var red = 0
        var green = 0
        var blue = 0
        var alpha = 255 // Standard: volle Deckkraft
        colorPreview.setBackgroundColor(Color.BLACK)


        // Erstelle den Dialog
        val dialog = AlertDialog.Builder(this)
            .setTitle("Choose a Color")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val selectedColor = Color.argb(alpha, red, green, blue)
                // Farbe ausgewählt, verwende sie wie gewünscht
                // Wende die ausgewählte Farbe auf alle Bitmaps in der Liste an
                applyColorToPanels(selectedColor)

                Log.d("ColorPicker", "Ausgewählte Farbe: #$selectedColor")
            }
            .setNegativeButton("Cancel", null)
            .create()

        // SeekBar-Listener, um die Farbe in Echtzeit zu ändern
        val colorChangeListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                when (seekBar?.id) {
                    R.id.seekBarRed -> red = progress
                    R.id.seekBarGreen -> green = progress
                    R.id.seekBarBlue -> blue = progress
                    R.id.seekBarAlpha -> alpha = progress // Setze den Alpha-Wert
                }
                color = Color.argb(alpha, red, green, blue)
                colorPreview.setBackgroundColor(color)


            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        // Setze die Listener für die SeekBars
        seekBarRed.setOnSeekBarChangeListener(colorChangeListener)
        seekBarGreen.setOnSeekBarChangeListener(colorChangeListener)
        seekBarBlue.setOnSeekBarChangeListener(colorChangeListener)
        seekBarAlpha.setOnSeekBarChangeListener(colorChangeListener) // Neue SeekBar für Alpha

        // Zeige den Dialog
        dialog.show()
    }

    private fun applyOverlayFilter(originalBitmap: Bitmap, color: Int): Bitmap {
        // Erstelle ein neues Bitmap mit den gleichen Dimensionen wie das Original
        val resultBitmap = Bitmap.createBitmap(
            originalBitmap.width, originalBitmap.height, originalBitmap.config
        )

        // Erstelle einen Canvas, um auf das neue Bitmap zu zeichnen
        val canvas = Canvas(resultBitmap)

        // Zeichne das ursprüngliche Bitmap auf das neue Bitmap
        canvas.drawBitmap(originalBitmap, 0f, 0f, null)

        // Erstelle ein Paint-Objekt mit einem Overlay-ColorFilter
        val paint = Paint()
        paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.OVERLAY)

        // Zeichne die Farbe als Overlay über das Bild
        canvas.drawBitmap(originalBitmap, 0f, 0f, paint)

        // Gib das resultierende Bitmap zurück
        return resultBitmap
    }

    private fun applyColorToPanels(selectedColor: Int) {
        for (i in panelList.indices) {
            val originalBitmap = panelList[i]
            val tintedBitmap = applyOverlayFilter(originalBitmap, selectedColor)
            panelList[i] = tintedBitmap // Ersetze das Original mit der eingefärbten Version
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            // Berechtigungen nur für ältere Versionen anfordern
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Berechtigung erteilt", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Berechtigung verweigert", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun togglePanelSwitching(item: MenuItem) {
        if (isPanelSwitchingActive) {
            item.setTitle("Start Automatic Panel Switching")
            stopPanelSwitching()
            //showToolbar()
        } else {
            item.setTitle("Stop Automatic Panel Switching")
            startPanelSwitching()
            //hideToolbar()
        }
    }

    /*fun calculateTimeForPanel(panelBitmap: Bitmap): Long {
        val width = panelBitmap.width
        val height = panelBitmap.height
        val area = width * height

        // Verhältnis von Höhe zu Breite
        val aspectRatio = height.toDouble() / width

        // Basiszeit berechnen
        val baseTime = when {
            area > 1_500_000 -> 20000L // Sehr große Panels: 20 Sekunden
            area > 1_000_000 -> 15000L // Große Panels: 15 Sekunden
            area > 500_000 -> 10000L   // Mittlere Panels: 10 Sekunden
            else -> 6500L              // Kleine Panels: 5.5 Sekunden
        }

        // Zeit basierend auf Proportionen anpassen
        val proportionalAdjustment = when {
            aspectRatio > 2.0 -> 1.2   // Sehr hohes Panel: 20 % mehr Zeit
            aspectRatio > 1.5 -> 1.1   // Moderat hohes Panel: 10 % mehr Zeit
            aspectRatio < 0.5 -> 1.2   // Sehr breites Panel: 20 % mehr Zeit
            aspectRatio < 0.8 -> 1.1   // Moderat breites Panel: 10 % mehr Zeit
            else -> 1.0                // Normales Verhältnis: Keine Änderung
        }

        when {
            area > 1_500_000 -> Log.d("area", "sehr großes Panel: 20 s")
            area > 1_000_000 -> Log.d("area", "großes Panel: 15 s")
            area > 500_000 -> Log.d("area", "mittleres Panel: 10 s")
            else -> Log.d("area", "kleines Panel: 5.5 s")
        }
        when {
            aspectRatio > 2.0 -> Log.d("aspectRatio", "Sehr hohes Panel: +20 %")
            aspectRatio > 1.5 -> Log.d("aspectRatio", "Moderat hohes Panel: +10 %")
            aspectRatio < 0.5 -> Log.d("aspectRatio", "Sehr breites Panel: +20 %")
            aspectRatio < 0.8 -> Log.d("aspectRatio", "Moderat breites Panel: +10 %")
            else -> Log.d("aspectRatio", "Normal hohes Panel: +0%")
        }



        return (baseTime * proportionalAdjustment * timerMultiplier).toLong()
    }*/

    fun calculateTimeForPanel(panelBitmap: Bitmap): Long {
        Log.d("SmoothDelay", "Funktion wurde aufgerufen")

        val width = panelBitmap.width
        val height = panelBitmap.height
        val area = width.toDouble() * height.toDouble()

        // Grundparameter
        val minBaseTime = 3000L     // 4 Sekunden Mindestanzeige
        val maxExtraTime = 10000L   // Bis zu 16 Sekunden Zusatzzeit möglich

        // Wurzel der Fläche, normiert auf typische Manga-Panelgrößen
        val normalizedArea =
            (area / 1_000_000.0).coerceIn(0.1, 3.0) // typisches Manga-Panel: 0.1M–3M Pixel

        // Zeit basierend auf Wurzel, damit große Panels weniger extrem eskalieren
        val areaFactor = Math.sqrt(normalizedArea)

        val dynamicTime = (areaFactor * maxExtraTime).toLong()

        // Seitenverhältnis (wie bei dir) als Bonusanpassung
        val aspectRatio = height / width.toDouble()
        val aspectMultiplier = when {
            aspectRatio > 2.5 -> 1.3
            aspectRatio > 2.0 -> 1.2
            aspectRatio > 1.5 -> 1.1
            aspectRatio < 0.5 -> 1.2
            aspectRatio < 0.8 -> 1.1
            else -> 1.0
        }

        val finalTime = ((minBaseTime + dynamicTime) * aspectMultiplier * timerMultiplier).toLong()

        Log.d(
            "SmoothDelay",
            "Area: $area, Normalized: $normalizedArea, BaseTime: ${minBaseTime + dynamicTime}, Final: $finalTime ms"
        )

        return finalTime
    }


    fun startPanelSwitching() {
        Log.d("startPanelSwitching", "Next")
        if (!isPanelSwitchingActive) {
            isPanelSwitchingActive = true

            // ProgressBar sichtbar machen
            fadeInProgressBar()

            // Erster Panel-Wechsel und Animation starten
            val currentBitmap = panelList[indexPanel % panelList.size]
            //binding.imageView2.setImageBitmap(currentBitmap)
            crossfadePanel(currentBitmap, binding.imageView2)

            // Hier wird die Fortschrittsbalken-Animation aufgerufen
            startProgressBarAnimation(calculateTimeForPanel(currentBitmap))
        }
    }

    fun stopPanelSwitching() {
        isPanelSwitchingActive = false
        progressBarPanel.progress = 0 // Fortschritt zurücksetzen
        panelSwitchTimer?.cancel()
        panelSwitchTimer = null
        // ProgressBar ausblenden
        fadeOutProgressBar()
    }

    private fun startProgressBarAnimation(duration: Long) {
        progressTaskRunnable?.let { handler.removeCallbacks(it) } // Vorherige Task abbrechen

        progressBarPanel.progress = 0
        progressBarPanel.max = 1000
        progressBarPanel.visibility = View.VISIBLE

        // ✨ Kontinuierliche, sanfte Animation
        val animator = ObjectAnimator.ofInt(progressBarPanel, "progress", 0, 1000).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (isPanelSwitchingActive) {
                        progressBarPanel.visibility = View.GONE

                        showNextPanel()

                        val nextBitmap = panelList[indexPanel % panelList.size]
                        val nextDuration =
                            calculateTimeForPanel(nextBitmap) // 🧠 Neue Dauer berechnen!
                        val next = nextDuration / 1000.00
                        Log.d("SmoothDelay", "Next Duration: $next")

                        startProgressBarAnimation(nextDuration) // Jetzt wird's dynamisch 🎉
                    }
                }


                override fun onAnimationCancel(animation: Animator) {
                    progressBarPanel.visibility = View.GONE
                }
            })
        }

        // 🔥 Wichtiger Schritt: Merke dir den Animator, um ihn ggf. zu beenden
        currentProgressAnimator?.cancel()
        currentProgressAnimator = animator
        animator.start()
    }


    private fun showNextPanel() {
        Log.d("showNextPanel", "Next")
        binding.imageView2.scaleX = 1f
        binding.imageView2.scaleY = 1f
        binding.imageView2.translationX = 0f
        binding.imageView2.translationY = 0f
        indexPanel++
        //binding.imageView2.setImageBitmap(panelList[indexPanel % panelList.size])
        crossfadePanel(panelList[indexPanel % panelList.size], binding.imageView2)
        textView.visibility = View.GONE
    }


    fun onManualPanelSwitch() {
        Log.d("onManualPanelSwitch", "Next")

        // Panelposition zurücksetzen
        binding.imageView2.scaleX = 1f
        binding.imageView2.scaleY = 1f
        binding.imageView2.translationX = 0f
        binding.imageView2.translationY = 0f

        // Zum nächsten Panel wechseln
        indexPanel++
        crossfadePanel(panelList[indexPanel % panelList.size], binding.imageView2)
        textView.visibility = View.GONE

        // Falls automatischer Modus aktiv ist:
        // -> Korrigiere indexPanel, damit der automatische Modus nicht das Panel doppelt überspringt
        // -> Starte Timer und Fortschrittsanzeige neu
        if (isPanelSwitchingActive) {
            indexPanel--
            restartPanelSwitching()
        }
    }


    fun animateProgressBar() {
        Log.d("animateProgressBar", "Next")
        // Aktuelles Panel bestimmen
        val currentBitmap = panelList[indexPanel % panelList.size]

        // Dauer dynamisch berechnen
        val duration = calculateTimeForPanel(currentBitmap)
        Log.d("interval", (duration.toFloat() / 1000f).toString())

        // Ladebalken animieren
        binding.progressBarPanel.animate()
            .setDuration(duration)
            .alpha(1f)
            .withEndAction {
                if (isPanelSwitchingActive) {
                    // Panel wechseln
                    indexPanel++
                    val nextBitmap = panelList[indexPanel % panelList.size]
                    //binding.imageView2.setImageBitmap(nextBitmap)
                    crossfadePanel(nextBitmap, binding.imageView2)

                    // Nächste Animation starten
                    animateProgressBar()
                }
            }
            .start()
    }


    fun fadeInProgressBar() {
        binding.progressBarPanel.apply {
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(300).start()
        }
    }

    fun fadeOutProgressBar() {
        binding.progressBarPanel.animate().alpha(0f).setDuration(300).withEndAction {
            binding.progressBarPanel.visibility = View.GONE
        }.start()
    }


    private fun startProgressBarAnimation(duration: Long, startProgress: Double = 0.0) {
        Log.d("startProgressBarAnimation", "Next")
        progressTaskRunnable?.let { handler.removeCallbacks(it) }

        progressBarPanel.progress = 0
        progressBarPanel.max = 1000
        progressBarPanel.visibility = View.VISIBLE

        val animator = ObjectAnimator.ofInt(progressBarPanel, "progress", 0, 1000).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (isPanelSwitchingActive) {
                        indexPanel = (indexPanel + 1) % panelList.size

                        val nextBitmap = panelList[indexPanel]
                        crossfadePanel(nextBitmap, binding.imageView2)

                        val nextDuration =
                            calculateTimeForPanel(nextBitmap) // 🧠 Hier neu berechnen!
                        Log.d("AutoPlay", "Next panel delay: $nextDuration ms")

                        startProgressBarAnimation(nextDuration)
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                    progressBarPanel.visibility = View.GONE
                }
            })
        }

        currentProgressAnimator?.cancel()
        currentProgressAnimator = animator
        animator.start()
    }

    private fun loadNextPanel() {
        indexPanel = (indexPanel + 1) % panelList.size  // Nächstes Panel setzen
        displayPanel(panelList[indexPanel])  // Panel aktualisieren
        startProgressBarAnimation(calculateTimeForPanel(panelList[indexPanel]))  // Fortschrittsbalken neu starten
    }


    private fun resumeProgressBarAnimation() {
        Log.d("PanelSwitch", "Animation fortsetzen") // Log beim Fortsetzen
        if (!isPaused || !isPanelSwitchingActive) {
            Log.d(
                "PanelSwitch",
                "Fortsetzung abgebrochen - Pausiert oder Inaktiv"
            ) // Log, wenn nicht fortgesetzt wird
            return
        }
        isPaused = false // Setze das Pausen-Flag zurück

        // Fortsetzen der Animation mit Restzeit und gespeichertem Fortschritt
        startProgressBarAnimation(remainingTime, pausedProgress) // Fortsetzen mit Restzeit
    }

    private fun switchToFullPageView() {
        Log.d("switchToFullPageView", "Next")
        val pageIndex = indexPanel // Nutzt den aktuellen Panel-Index als Seiten-Index

        if (pageIndex in fullPageList.indices) {
            //binding.imageView2.setImageBitmap(fullPageList[pageIndex]) // Ganze Seite anzeigen
            crossfadePanel(fullPageList[pageIndex], binding.imageView2)
            binding.imageView2.scaleX = 1.0f
            binding.imageView2.scaleY = 1.0f
        }
    }


    private fun switchToPanelView() {
        isFullPageView = false
        displayPanel(panelList[indexPanel % panelList.size]) // Zur Panel-Ansicht zurückkehren
        Log.d("Zoom", "Zur Panel-Ansicht wechseln")
    }

    fun loadAppOpenAd(context: Context) {
        val adRequest = AdRequest.Builder().build()
        AppOpenAd.load(
            context,
            "ca-app-pub-5980216243664680/7574411330",  // Ersetze durch deine echte Ad Unit ID
            adRequest,
            AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    Log.d("AdMob", "App Open Ad geladen.")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.d("AdMob", "Fehler beim Laden der Anzeige: ${loadAdError.message}")
                }
            }
        )
        fun showAppOpenAd(activity: Activity) {
            appOpenAd?.let {
                it.show(activity)
            } ?: Log.d("AdMob", "App Open Ad nicht verfügbar.")
        }
    }

    fun showAppOpenAd(activity: Activity) {
        appOpenAd?.let {
            it.show(activity)
        } ?: Log.d("AdMob", "App Open Ad nicht verfügbar.")
    }

    override fun onStart() {
        super.onStart()
        loadAppOpenAd(this)
        showAppOpenAd(this)
    }

    private fun showMultiplierDialog() {
        val dialog = AlertDialog.Builder(this)
        dialog.setTitle("Set Timer Multiplier")

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.hint = "Min: 0.5 (Faster), Max: 3.0 (Slower), Normal: 1.0"
        dialog.setView(input)

        dialog.setPositiveButton("Set") { _, _ ->
            val userInput = input.text.toString()
            if (userInput.isNotEmpty()) {
                val multiplier = userInput.toFloatOrNull() ?: 1.0f
                if (multiplier in 0.5f..3.0f) {
                    timerMultiplier = multiplier
                    Toast.makeText(this, "Timer Speed set to x$timerMultiplier", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    Toast.makeText(this, "Value must be between 0.5 and 3.0", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
        dialog.setNegativeButton("Cancel", null)
        dialog.show()
    }

    // Funktion, um die Toolbar ein- oder auszublenden
    private fun toggleToolbar() {
        if (isToolbarVisible) {
            supportActionBar?.hide()
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        } else {
            supportActionBar?.show()
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
        isToolbarVisible = !isToolbarVisible
    }

    private fun toggleToolbarAnimated() {
        if (isToolbarVisible) {

            // Ausblenden
            toolbar.animate()
                .translationY(-toolbar.height.toFloat())
                .alpha(0f)
                .setDuration(250)
                .withEndAction {
                    toolbar.visibility = View.GONE
                }
                .start()

            enterImmersiveMode()


        } else {

            // Sichtbar machen
            toolbar.visibility = View.VISIBLE
            toolbar.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(250)
                .withEndAction {
                    // 🔁 Toolbar wieder verbinden und Menü neu laden
                    setSupportActionBar(toolbar)
                    invalidateOptionsMenu()
                }
                .start()

            exitImmersiveMode()
        }

        isToolbarVisible = !isToolbarVisible
    }

    private fun getActionBarView(): View? {
        val decorView = window.decorView as ViewGroup
        return decorView.findViewById<View>(
            resources.getIdentifier(
                "action_bar_container",
                "id",
                "android"
            )
        )
    }


    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
    }

    private fun showSystemUI() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    private fun toggleReadingDirection() {
        isReadingRightToLeft = !isReadingRightToLeft
        panelList.clear()
        //listFilesInFolder(selectedFolderUri!!)
        if (documentFile != null && documentFile!!.isDirectory) {
            processChapters(documentFile!!)
        }
    }

    private fun showJumpToDialog() {
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle("Jump to...")

        // Eingabefeld für die Panel- oder Seitennummer
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Enter Panel Number from 1 to ${panelList.size}"
        }
        dialogBuilder.setView(input)

        // Positive Schaltfläche zum Springen
        dialogBuilder.setPositiveButton("Jump") { _, _ ->
            val inputText = input.text.toString()
            val panelIndex = inputText.toIntOrNull()

            if (panelIndex != null && panelIndex in 1..panelList.size) {
                jumpToPanel(panelIndex - 1) // Panels sind 0-basiert
            } else {
                Toast.makeText(this, "Index Out of Bounds: ${inputText}", Toast.LENGTH_SHORT).show()
            }
        }

        // Negative Schaltfläche zum Abbrechen
        dialogBuilder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        // Dialog anzeigen
        dialogBuilder.create().show()
    }

    private fun jumpToPanel(panelIndex: Int) {
        indexPanel = panelIndex
        displayPanel(panelList[panelIndex])
    }

    private fun displayPanel(panelBitmap: Bitmap) {
        runOnUiThread {
            //binding.imageView2.setImageBitmap(panelBitmap)
            Log.d("displayPanel", "Next")
            crossfadePanel(panelBitmap, binding.imageView2)
        }
    }

    private fun showHintDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Controls")
        val instructionText = """
        <b>Tap:</b> Move to the next panel.<br>
        <b>Long Press:</b> Go back to the previous panel.<br>
        <b>Double Tap:</b> Zoom in or out on the current panel.<br>
        <b>Drag:</b> Pan around a zoomed panel.<br>
        <b>Hide Taskbar:</b> Fling panel upwards/downwards to hide/show the taskbar.<br>
        <b>Auto Mode:</b> Enable in the menu to automatically navigate through panels.<br>
        <b>Full Page View:</b> Double Tap twice or pinch out to zoom out of the panel to view the corresponding entire page in one go.<br><br>
        Enjoy your reading experience. 📚✨
    """
        builder.setMessage(Html.fromHtml(instructionText, Html.FROM_HTML_MODE_COMPACT))
        builder.setPositiveButton("Understood") { dialog, _ ->
            dialog.dismiss() // Schließt den Dialog
        }
        builder.setCancelable(false) // Dialog kann nicht durch Tippen außerhalb geschlossen werden
        builder.show()
    }

    fun crossfadePanel(newBitmap: Bitmap, imageView: ImageView) {
        if (imageView.drawable == null) {
            // Erstes Bild: direkt setzen, ohne Animation
            imageView.setImageBitmap(newBitmap)
            imageView.alpha = 1f
            return
        }

        val fadeOut = ObjectAnimator.ofFloat(imageView, "alpha", 1f, 0f)
        fadeOut.duration = 150

        val fadeIn = ObjectAnimator.ofFloat(imageView, "alpha", 0f, 1f)
        fadeIn.duration = 150

        fadeOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                imageView.setImageBitmap(newBitmap)
                fadeIn.start()
            }
        })

        fadeOut.start()

        fadeIn.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                Log.d("crossfade", "Fade in abgeschlossen")
            }
        })

    }


    private fun hideToolbar() {
        supportActionBar?.hide()
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
    }

    private fun showToolbar() {
        supportActionBar?.show()
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    fun enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    fun exitImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    fun animateProgressBarTo(progressBar: ProgressBar, targetProgress: Int) {
        val animation =
            ObjectAnimator.ofInt(progressBar, "progress", progressBar.progress, targetProgress)
        animation.duration = 500  // Dauer der Animation in ms
        animation.interpolator = DecelerateInterpolator()
        animation.start()
    }

    private fun restartPanelSwitching() {
        // Nur den Timer und Animator stoppen, aber den Modus beibehalten
        panelSwitchTimer?.cancel()
        currentProgressAnimator?.cancel()

        // Aktuelles Panel neu starten
        startPanelSwitching() // startet Timer und Fortschrittsanimation für das aktuelle Panel neu
    }

    fun onManualPanelBack() {
        Log.d("onManualPanelBack", "Previous")

        // Panelposition zurücksetzen
        binding.imageView2.scaleX = 1f
        binding.imageView2.scaleY = 1f
        binding.imageView2.translationX = 0f
        binding.imageView2.translationY = 0f

        // Vorheriges Panel anzeigen (achte auf negativen Index)
        if (indexPanel > 0) {
            indexPanel--
        }

        crossfadePanel(panelList[indexPanel % panelList.size], binding.imageView2)
        textView.visibility = View.GONE

        // Automatik-Modus neu starten (aber nicht deaktivieren)
        if (isPanelSwitchingActive) {
            indexPanel--
            restartPanelSwitching()
        }
    }

    private fun saveAllPanels() {
        CoroutineScope(Dispatchers.IO).launch {
            val timeStamp = System.currentTimeMillis()
            var successCount = 0

            for ((i, bitmap) in panelList.withIndex()) {
                val filename = "manga_panel_${timeStamp}_$i.png"

                try {
                    val outputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MangaPanels")
                        }

                        val uri = contentResolver.insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            values
                        )
                        uri?.let { contentResolver.openOutputStream(it) }
                            ?: throw IOException("Fehler beim Erstellen der Datei")
                    } else {
                        val directory = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                            "MangaPanels"
                        )
                        if (!directory.exists()) directory.mkdirs()
                        val file = File(directory, filename)
                        FileOutputStream(file)
                    }

                    outputStream.use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }

                    successCount++

                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    "$successCount von ${panelList.size} Panels gespeichert 📂",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun saveLargePanelsOnly() {
        CoroutineScope(Dispatchers.IO).launch {
            val thresholdArea = 600_000  // Mindestfläche in Pixeln
            val timeStamp = System.currentTimeMillis()
            var successCount = 0

            for ((i, bitmap) in panelList.withIndex()) {
                val area = bitmap.width * bitmap.height
                if (area < thresholdArea) continue  // Überspringe kleine Panels

                val filename = "manga_panel_${timeStamp}_$i.png"

                try {
                    val outputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                            put(
                                MediaStore.Images.Media.RELATIVE_PATH,
                                "Pictures/MangaPanels/LargeOnly"
                            )
                        }

                        val uri = contentResolver.insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            values
                        )
                        uri?.let { contentResolver.openOutputStream(it) }
                            ?: throw IOException("Fehler beim Erstellen der Datei")
                    } else {
                        val directory = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                            "MangaPanels/LargeOnly"
                        )
                        if (!directory.exists()) directory.mkdirs()
                        val file = File(directory, filename)
                        FileOutputStream(file)
                    }

                    outputStream.use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }

                    successCount++

                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    "$successCount large panels saved 🖼️",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun handleDrag(event: MotionEvent, view: View) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.rawX
                lastY = event.rawY
                isDragging = currentScale > 1f
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val dx = event.rawX - lastX
                    val dy = event.rawY - lastY

                    binding.imageView2.translationX += dx
                    binding.imageView2.translationY += dy

                    lastX = event.rawX
                    lastY = event.rawY
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
    }

    private fun handleLongPressAndSwipe(event: MotionEvent, view: View) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                dX = view.x - lastTouchX
                dY = view.y - lastTouchY

                isLongPress = true
                longPressHandler = Handler()
                longPressHandler?.postDelayed({
                    if (isLongPress && !isDragging) {
                        // Panel zurück
                        if (indexPanel > 0) indexPanel--
                        crossfadePanel(panelList[indexPanel], binding.imageView2)
                        textView.visibility = View.GONE
                    }
                }, longClickDuration)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isSwiping) {
                    val deltaX = event.rawX - lastTouchX
                    val deltaY = event.rawY - lastTouchY
                    if (!isDragging && (Math.abs(deltaX) > touchSlop || Math.abs(deltaY) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        dragViewWithinBounds(view, event.rawX + dX, event.rawY + dY)
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.isPressed = false
                longPressHandler?.removeCallbacksAndMessages(null)
                isLongPress = false
                isDragging = false
                isSwiping = false
            }
        }
    }

    private fun dragViewWithinBounds(view: View, newX: Float, newY: Float) {
        val dragThreshold = Math.max(view.width, view.height) * 0.45f
        val parent = view.parent as View
        val minX = -dragThreshold
        val maxX = parent.width - view.width + dragThreshold
        val minY = -dragThreshold
        val maxY = parent.height - view.height + dragThreshold

        val constrainedX = newX.coerceIn(minX, maxX.toFloat())
        val constrainedY = newY.coerceIn(minY, maxY.toFloat())

        view.animate()
            .x(constrainedX)
            .y(constrainedY)
            .setDuration(0)
            .start()
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }
}
