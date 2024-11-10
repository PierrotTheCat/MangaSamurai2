package com.kurai.mangasamurai2

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.github.pedrovgs.deeppanel.DeepPanel
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.kurai.mangasamurai2.databinding.ActivityMainBinding
import android.os.Bundle as nBundle
import android.widget.Toast
import kotlinx.coroutines.*
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.material.color.MaterialColors
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import androidx.core.view.GestureDetectorCompat
import java.util.Timer
import kotlin.concurrent.timerTask


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    val i=0
    var indexPanel: Int = -1
    var indexPage: Int = 0
    private val pageList = ArrayList<DocumentFile>()
    val panelList = ArrayList<Bitmap>()
    private var selectedFolderUri: Uri? = null
    private lateinit var progressBar: ProgressBar
    private lateinit var textView: TextView
    private lateinit var adView: AdView
    var color: Int = Color.argb(120,120,120,120)
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

    private val PICK_FOLDER_REQUEST_CODE = 123
    private var folderPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Log.d("folderpickerlauncher", "true")
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            data?.data?.also { uri ->
                // ProgressBar anzeigen
                progressBar.visibility = View.VISIBLE

                // Coroutine starten, um den Schneidevorgang im Hintergrund auszuführen
                CoroutineScope(Dispatchers.Default).launch {
                    // Hier den Schneidevorgang durchführen
                    selectedFolderUri = uri
                    listFilesInFolder(selectedFolderUri!!)

                    // ProgressBar auf dem Haupt-UI-Thread ausblenden
                    runOnUiThread {
                        progressBar.visibility = View.GONE

                        val imageView: ImageView = binding.imageView2
                        //progressBar.visibility = View.GONE
                        textView.text = "Tap to start. \n\nControls: tap to move to the next panel, long press to move back. Pinch or double tap to zoom."


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
        adView = findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        textView = findViewById(R.id.text_home)
        textView.visibility = View.VISIBLE

        // Initialisiere die ProgressBar
        progressBar = findViewById(R.id.progressBar)

        // Setze die Sichtbarkeit der ProgressBar auf GONE, um sie zunächst unsichtbar zu machen
        progressBar.visibility = View.GONE

        DeepPanel.initialize(this)

        //Log.d("onviewcreated", "before")
        //openFolderPicker()
        //Log.d("onviewcreated", "after")

        val imageView: ImageView = binding.imageView2

// Initialisiere GestureDetector für DoubleTap und SingleTap
        val gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                if(!isZoomed && !isDragging) {
                    indexPanel--
                    imageView.setImageBitmap(panelList[indexPanel % panelList.size])
                    textView.visibility = View.GONE
                }

                super.onLongPress(e)
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Bei DoubleTap zoomt das Bild zurück auf die Standardgröße
                scaleFactor = if (scaleFactor > 1.0f) 1.0f else 2.0f
                imageView.scaleX = scaleFactor
                imageView.scaleY = scaleFactor
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                longPressHandler?.removeCallbacksAndMessages(null) // Stoppe den Long-Press Timer

                isLongPress = false // Setze isLongPress zurück
                // Aktion für Single Tap
                if(isDragging==false) {
                    if (e.eventTime - e.downTime < ViewConfiguration.getLongPressTimeout()) {
                        // Wechsel zum nächsten Panel
                        imageView.scaleX = 1f
                        imageView.scaleY = 1f
                        imageView.translationX = 0f
                        imageView.translationY = 0f
                        indexPanel++
                        imageView.setImageBitmap(panelList[indexPanel % panelList.size])
                        textView.visibility = View.GONE
                    }
                }
                return true
            }
        })

        // Pinch-Zooming
        val scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isZoomed = true
                return super.onScaleBegin(detector)
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isZoomed = false
                super.onScaleEnd(detector)
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                // Aktualisiere das Ziel, ohne die Skalierung sofort anzupassen
                targetScaleFactor *= detector.scaleFactor
                targetScaleFactor = Math.max(1.0f, Math.min(targetScaleFactor, 5.0f))

                // Entferne eventuell anstehende Zoom-Updates
                pendingZoomRunnable?.let { zoomHandler.removeCallbacks(it) }

                // Setze das verzögerte Zoom-Update
                pendingZoomRunnable = Runnable {
                    imageView.scaleX = targetScaleFactor
                    imageView.scaleY = targetScaleFactor
                }
                zoomHandler.postDelayed(pendingZoomRunnable!!, 50) // Aktualisierung alle 50ms

                return true
            }
        })

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        isDragging = false
        val dragThreshold = 100 // Pixel, die das Panel über den sichtbaren Bereich hinaus bewegt werden kann


        // Setze den OnTouchListener für das ImageView
        imageView.setOnTouchListener { view, event ->
            // Wenn Pinch-to-Zoom aktiv ist, verarbeite nur das Zoomen
            gestureDetector.onTouchEvent(event) // Double Tap, Single Tap
            scaleGestureDetector.onTouchEvent(event)


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
                    indexPanel--
                    imageView.setImageBitmap(panelList[indexPanel % panelList.size])
                    textView.visibility = View.GONE
                }
            }, longClickDuration)
        }

        MotionEvent.ACTION_MOVE -> {
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
                        if(fileName?.get(0)!!.isDigit()) {
                            pageList.add(file)
                        }
                    }
                }


            } else {
                Log.d("error", "files is null")
            }

            pageList.sortBy { it.name }
            // Zugriff auf pageList hier nach dem Hinzufügen der Dateien
            Log.d("pageListsize",pageList.size.toString())
            for (page in pageList) {
                page.name?.let { Log.d("pageList", it) }
            }


            val deepPanel = DeepPanel()

            for (page in pageList) {
                var bitmap = BitmapFactory.decodeStream(
                    this.contentResolver.openInputStream(page.uri)
                )

                val result = deepPanel.extractPanelsInfo(bitmap)
                var temp = result.panels.panelsInfo
                var temp2 = temp.sortedByDescending { it.left }
                var temp3 = temp2.sortedBy { it.top }
                temp3.forEach { panel ->
                    Log.d("DeepPanel", """Left: ${panel.left}, Top: ${panel.top}
                        |Right: ${panel.right}, Bottom: ${panel.bottom}
                        |Width: ${panel.width}, Height: ${panel.height}
                    """.trimMargin())
                    panelList.add(Bitmap.createBitmap(bitmap,panel.left,panel.top,panel.width,panel.height))
                }
            }


        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d("onactivityresult", "true")
        if (requestCode == PICK_FOLDER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.also { uri ->
                selectedFolderUri = uri
                listFilesInFolder(selectedFolderUri!!)
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
                openFolderPicker()
                true
            }
            R.id.action_option2 -> {
                // Aktion für Option 2 - Zeige den Farbwähldialog an
                showColorPickerDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showColorPickerDialog() {
        // Lade das Farbwähler-Layout
        val dialogView = LayoutInflater.from(this).inflate(R.layout.color_picker_dialog, null)

        val seekBarRed = dialogView.findViewById<SeekBar>(R.id.seekBarRed)
        val seekBarGreen = dialogView.findViewById<SeekBar>(R.id.seekBarGreen)
        val seekBarBlue = dialogView.findViewById<SeekBar>(R.id.seekBarBlue)
        val seekBarAlpha = dialogView.findViewById<SeekBar>(R.id.seekBarAlpha) // Neue SeekBar für Alpha

        var red = 0
        var green = 0
        var blue = 0
        var alpha = 255 // Standard: volle Deckkraft

        // Aktuelle Farbe anzeigen (du kannst diese Ansicht auch in deinem Layout hinzufügen)
        val selectedColorTextView = TextView(this)
        selectedColorTextView.text = "Aktuelle Farbe: #000000"
        selectedColorTextView.setBackgroundColor(Color.argb(alpha, red, green, blue))

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
                selectedColorTextView.text = String.format("Aktuelle Farbe: #%06X", 0xFFFFFF and color)
                selectedColorTextView.setBackgroundColor(color)


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






}