package com.example.wlur

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : ComponentActivity() {

    private var imageBitmap by mutableStateOf<Bitmap?>(null)
    private val permissionRequestCode = 123

    private val requestPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // Permiso de escritura en la galería concedido
            } else {
                Toast.makeText(
                    this,
                    "Permiso de escritura en la galería denegado",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val requestSaveImageLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Toast.makeText(this, "Imagen guardada en la galería", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Error al guardar la imagen", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            var blurIntensity by remember { mutableStateOf(0f) }

            // Solicitar permiso de escritura en la galería
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

            // Diseño de la interfaz de usuario con JetPack Compose
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ImageView para mostrar la imagen
                imageBitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.8f)
                    )
                }

                // Deslizador de intensidad de desenfoque
                Slider(
                    value = blurIntensity,
                    onValueChange = { newIntensity ->
                        blurIntensity = newIntensity
                        imageBitmap?.let {
                            val blurredBitmap = applyGaussianBlur(it, blurIntensity)
                            imageBitmap = blurredBitmap
                        }
                    },
                    valueRange = 0f..25f,
                    modifier = Modifier
                        .fillMaxWidth()
                )

                // Botón para abrir la galería y cargar la imagen
                Button(
                    onClick = {
                        openGallery(imageBitmap)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Text("Cargar Imagen")
                }

                // Botón para guardar en la galería
                Button(
                    onClick = {
                        imageBitmap?.let {
                            saveImageToGallery(it)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Text("Guardar en Galería")
                }
            }
        }
    }

    // Método para aplicar el desenfoque gaussiano
    private fun applyGaussianBlur(inputBitmap: Bitmap, radius: Float): Bitmap {
        val outputBitmap =
            Bitmap.createBitmap(inputBitmap.width, inputBitmap.height, inputBitmap.config)

        val rs = RenderScript.create(this)
        val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        val inAlloc = Allocation.createFromBitmap(rs, inputBitmap)
        val outAlloc = Allocation.createFromBitmap(rs, outputBitmap)
        script.setRadius(radius)
        script.setInput(inAlloc)
        script.forEach(outAlloc)
        outAlloc.copyTo(outputBitmap)

        return outputBitmap
    }

    // Método para guardar la imagen en la galería
    private fun saveImageToGallery(bitmap: Bitmap) {
        // Comprueba si el permiso de escritura en la galería está otorgado
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val imagesDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val imageFile = File(imagesDir, "blurred_image.png")

            try {
                val stream = FileOutputStream(imageFile)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.flush()
                stream.close()

                // Escanear la imagen para que aparezca en la galería
                MediaStore.Images.Media.insertImage(
                    contentResolver,
                    imageFile.absolutePath,
                    imageFile.name,
                    null
                )

                Toast.makeText(this, "Imagen guardada en la galería", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Toast.makeText(this, "Error al guardar la imagen", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Si no se otorgó el permiso, solicítalo nuevamente
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    // Método para abrir la galería
    private fun openGallery(imageBitmap: Bitmap?) {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, 1)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            val selectedImageUri: Uri? = data.data
            if (selectedImageUri != null) {
                loadSelectedImage(selectedImageUri)
            }
        }
    }

    private fun loadSelectedImage(imageUri: Uri) {
        val requestOptions = RequestOptions()
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)

        Glide.with(this)
            .asBitmap()
            .load(imageUri)
            .apply(requestOptions)
            .into(object : SimpleTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    imageBitmap = resource // Asigna la imagen a la propiedad de la clase
                }
            })
    }
}
