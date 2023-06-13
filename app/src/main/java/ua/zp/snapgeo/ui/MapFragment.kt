package ua.zp.snapgeo.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import jp.wasabeef.glide.transformations.CropCircleWithBorderTransformation
import jp.wasabeef.glide.transformations.CropSquareTransformation
import jp.wasabeef.glide.transformations.RoundedCornersTransformation
import jp.wasabeef.glide.transformations.RoundedCornersTransformation.CornerType
import ua.zp.snapgeo.R
import ua.zp.snapgeo.data.PhotoLocation
import ua.zp.snapgeo.databinding.FragmentMapBinding
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    var mLocationRequest: LocationRequest? = null
    var mLastLocation: Location? = null


    var mFusedLocationClient: FusedLocationProviderClient? = null

    private lateinit var mGoogleMap: GoogleMap

    private lateinit var photoURI: Uri

    private lateinit var takePictureResult: ActivityResultLauncher<Intent>


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        takePictureResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val photoLocation = getPhotoLocation(photoURI)
                    photoLocation?.let {
                        addMarkerPhotoToMap(it)
                    }
                } else {
                    // зняття фотографії було скасоване або відбулася помилка
                }
            }

        binding.fabPhotoCamera.setOnClickListener {
            takePictureAndSaveToGallery()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mGoogleMap = googleMap

        mLocationRequest =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 12000).apply {
                setWaitForAccurateLocation(true)
                setMaxUpdateAgeMillis(100000)
            }.build()

        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mFusedLocationClient!!.requestLocationUpdates(
                mLocationRequest!!, mLocationCallback, Looper.myLooper()
            )
            mGoogleMap.isMyLocationEnabled = true
        } else {
            checkPermissions()
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH
        )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%SnapGeo%")
        val cursor = requireActivity().contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )

        cursor?.use {
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                val photoLocation = getPhotoLocation(contentUri)
                photoLocation?.let {
                    addMarkerPhotoToMap(it)
                }
            }
        }

    }

    override fun onPause() {
        super.onPause()

        //stop location updates when Activity is no longer active
        mFusedLocationClient?.removeLocationUpdates(mLocationCallback)
    }

    private var mLocationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val locationList = locationResult.locations
            if (locationList.size > 0) {

                val location = locationList[locationList.size - 1]
                mLastLocation = location
                val latLng = LatLng(location.latitude, location.longitude)

                mGoogleMap.animateCamera(CameraUpdateFactory.newLatLng(latLng))

                val cameraPosition =
                    CameraPosition.Builder().target(LatLng(latLng.latitude, latLng.longitude))
                        .zoom(18f).build()
                mGoogleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
            }
        }
    }

    private fun addMarkerPhotoToMap(photoLocation: PhotoLocation) {
        val location = LatLng(photoLocation.latitude, photoLocation.longitude)

        Glide.with(requireContext())
            .asBitmap()
            .load(photoLocation.photoUri)
            .override(100, 100)
            .transform(RoundedCornersTransformation(10,0))
            .into(object : CustomTarget<Bitmap>(){
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    mGoogleMap.addMarker(
                        MarkerOptions()
                            .position(location)
                            .title(photoLocation.address)
                            .icon(BitmapDescriptorFactory.fromBitmap(resource))
                    )
                    mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15f))
                }

                override fun onLoadCleared(placeholder: Drawable?) {

                }
            }
        )
    }

    private fun takePictureAndSaveToGallery() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(requireActivity().packageManager)?.also {
                val timeStamp: String =
                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val filename = "JPEG_${timeStamp}_"

                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$filename.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + File.separator + "SnapGeo"
                    )
                }

                val resolver = requireContext().contentResolver
                photoURI = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!

                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                takePictureResult.launch(takePictureIntent)
            }

        }
    }

    private fun getPhotoLocation(photoUri: Uri): PhotoLocation? {
        val inputStream: InputStream? = try {
            requireContext().contentResolver.openInputStream(photoUri)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            return null
        }

        val exifInterface = inputStream?.let {
            try {
                ExifInterface(it)
            } catch (e: IOException) {
                e.printStackTrace()
                return null
            }
        }

        val latLong = FloatArray(2)
        if (exifInterface?.getLatLong(latLong) == true) {
            val latitude = latLong[0].toDouble()
            val longitude = latLong[1].toDouble()
            val address = getAddressFromCoordinates(requireContext(), latitude, longitude)
            return PhotoLocation(latitude, longitude, address, photoUri)
        }

        return null
    }

    private fun getAddressFromCoordinates(
        context: Context,
        latitude: Double,
        longitude: Double
    ): String {
        val geocoder = Geocoder(context, Locale.getDefault())
        var addressText = ""

        try {
            val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address: Address = addresses[0]
                addressText = address.getAddressLine(0)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return addressText
    }

    private fun checkPermissions() {
        Dexter.withContext(requireContext())
            .withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CAMERA
            )
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                    if (report.areAllPermissionsGranted()) {
                        if (ActivityCompat.checkSelfPermission(
                                requireContext(),
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                                requireContext(),
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return
                        }
                        mFusedLocationClient!!.requestLocationUpdates(
                            mLocationRequest!!, mLocationCallback, Looper.myLooper()
                        )
                        mGoogleMap.isMyLocationEnabled = true
                    }

                    if (report.isAnyPermissionPermanentlyDenied) {
                        // navigate user to app settings
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    AlertDialog.Builder(requireContext()).setTitle("Permissions Required")
                        .setMessage("This app needs the Location, Camera, and Write External Storage permissions, please accept to use all functionalities")
                        .setPositiveButton(
                            "OK"
                        ) { _, _ ->
                            token?.continuePermissionRequest()
                        }.create().show()
                }
            }).check()
    }
}
