package com.kurai.mangasamurai2

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
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



class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    val i=0
    var indexPanel: Int = 0
    var indexPage: Int = 0
    private val pageList = ArrayList<DocumentFile>()
    val panelList = ArrayList<Bitmap>()
    private var selectedFolderUri: Uri? = null
    private lateinit var progressBar: ProgressBar
    private lateinit var textView: TextView


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
                        textView.text = "Tap to start. \n\nControls: tap to move to the next panel, long press to move back."


                        val size = panelList.size
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
                        }
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
                // Zeige die ProgressBar an, um den Ladevorgang anzuzeigen
                progressBar.visibility = View.VISIBLE
                pageList.clear()
                Log.d("onviewcreated", "before")
                openFolderPicker()
                Log.d("onviewcreated", "after")
                true
            }
            R.id.action_option2 -> {
                // Aktion für Option 2
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}