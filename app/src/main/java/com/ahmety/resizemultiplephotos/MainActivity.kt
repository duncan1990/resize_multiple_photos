package com.ahmety.resizemultiplephotos

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.ahmety.resizemultiplephotos.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import android.Manifest

class MainActivity : Activity() {

    private lateinit var binding: ActivityMainBinding
    private val REQUEST_CODE = 200
    private val REQUEST_CODE_POST_NOTIFICATIONS = 1001
    private var photosCountArray = arrayListOf<Int>()
    private val uriList = mutableListOf<Uri>()
    private var adapter: PhotosListAdapter? = null
    private var uriForNotification: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val layoutManager = GridLayoutManager(this, 4)
        binding.rvPhotos.layoutManager = layoutManager
        adapter = PhotosListAdapter(uriList, this)
        binding.rvPhotos.adapter = adapter
        binding.btnSelectPhoto.setOnClickListener {
            selectPhotos()
        }

        binding.btnResizePhoto.setOnClickListener {
            resizeImages()
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            val clipData = data?.clipData

            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    uriList.add(clipData.getItemAt(i).uri)
                    photosCountArray.add(1)
                }
            } else {
                data?.data?.let {
                    uriList.add(it)
                    photosCountArray.add(1)
                }
            }
            photosCountArray
            adapter?.notifyDataSetChanged()
            //resizeImages(uriList)
        }
    }

    private fun selectPhotos() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) // Çoklu seçim
        }
        startActivityForResult(Intent.createChooser(intent, "Resimleri Seçin"), REQUEST_CODE)
    }

    private fun resizeImages() {
        uriList.forEachIndexed { index, uri ->
            val inputStream = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)

            val resizedBitmap = Bitmap.createScaledBitmap(
                originalBitmap,
                if (binding.etWidth.text.toString().isNotEmpty()) binding.etWidth.text.toString().toInt() else 400,
                if (binding.etHeight.text.toString().isNotEmpty()) binding.etHeight.text.toString().toInt() else 800, true
            )
            if (index == uriList.lastIndex) {
                // Listenin son öğesi
                saveImageToDownloads(resizedBitmap, isLastPhoto = true, isFirstPhoto = false)
            } else {
                if (index == 0) {
                    setProgressScreen(false)
                    binding.progressBar.visibility = View.VISIBLE
                    saveImageToDownloads(resizedBitmap, isLastPhoto = false, isFirstPhoto = true)
                }
                saveImageToDownloads(resizedBitmap, isLastPhoto = false, isFirstPhoto = false)
            }
        }
    }

    private fun saveBitmap(bitmap: Bitmap) {
        val filename = "${System.currentTimeMillis()}.jpg"
        val file = File(getExternalFilesDir(null), filename)

        FileOutputStream(file).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        }

        //Log.d("SaveBitmap", "Resim kaydedildi: ${file.absolutePath}")
    }

    private fun saveImageToDownloads(bitmap: Bitmap, isLastPhoto: Boolean, isFirstPhoto: Boolean) {
        val filename = "${System.currentTimeMillis()}.jpg"
        val contentResolver = applicationContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$filename.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        uri?.let {
            contentResolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            }
            if (isLastPhoto){
                setProgressScreen(true)
                binding.progressBar.visibility = View.GONE
                uriList.clear()
                adapter?.notifyDataSetChanged()
            }
            if (isFirstPhoto)
                showDownloadNotification(it)
            Log.d("SaveImage", getString(R.string.image_saved_to_downloads, uri))
        } ?: run {
            if (isLastPhoto){
                setProgressScreen(true)
                binding.progressBar.visibility = View.GONE
                uriList.clear()
                adapter?.notifyDataSetChanged()
            }

            Log.e("SaveImage", getString(R.string.failed_to_save_image))
        }
    }

    private fun showDownloadNotification(uri: Uri) {
        uriForNotification = uri
        if (!checkNotificationPermission()) {
            requestNotificationPermission()
            return // İzin verilmeden bildirim göndermeyin
        }
        val channelId = "downloads_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "Downloads",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "image/*")
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.ndirme_tamamland))
            .setContentText(getString(R.string.dosya_indirildi, uri))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        Toast.makeText(this, getString(R.string.file_downloaded), Toast.LENGTH_LONG).show()
        notificationManager.notify(1, notification)
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 13 öncesinde izin gerekmez
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_CODE_POST_NOTIFICATIONS
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_POST_NOTIFICATIONS &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            // Kullanıcı izin verdi, şimdi bildirimi gösterebilirsiniz
            uriForNotification?.let {
                showDownloadNotification(it)
            }
        } else {
            Toast.makeText(this, getString(R.string.rejected_permission), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setProgressScreen(isProgressCompleted: Boolean) {
        binding.tvDefault.alpha = if (isProgressCompleted) 1f else 0.20f
        binding.tvEnterWidthHeight.alpha = if (isProgressCompleted) 1f else 0.20f
        binding.llMeasure.alpha = if (isProgressCompleted) 1f else 0.20f
        binding.btnSelectPhoto.alpha = if (isProgressCompleted) 1f else 0.20f
        binding.btnResizePhoto.alpha = if (isProgressCompleted) 1f else 0.20f
        binding.rvPhotos.alpha = if (isProgressCompleted) 1f else 0.20f
    }

    override fun onDestroy() {
        super.onDestroy()
        adapter = null
    }

}