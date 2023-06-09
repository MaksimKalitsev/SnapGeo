package ua.zp.snapgeo.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
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
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import ua.zp.snapgeo.R
import ua.zp.snapgeo.data.PhotoLocation
import ua.zp.snapgeo.databinding.FragmentMapBinding
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

//    private lateinit var viewModel: MapViewModel

    private val MY_PERMISSIONS_REQUEST_LOCATION = 99

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
                    // фотографія була успішно знята, ваш файл наявний в photoFile
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

        requireContext().filesDir
        requireContext().cacheDir
//        val photoFilePath = photoFile.absolutePath
//        val photoLocation = getPhotoLocation(photoFile)

//        if (photoLocation != null) {
//            addMarkerPhotoToMap(photoLocation)
//        }

        mLocationRequest =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 12000).apply {
                setWaitForAccurateLocation(true)
                setMaxUpdateAgeMillis(100000)
            }.build()

        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            //Location Permission already granted
            mFusedLocationClient!!.requestLocationUpdates(
                mLocationRequest!!, mLocationCallback, Looper.myLooper()
            )
            mGoogleMap.isMyLocationEnabled = true
        } else {
            //Request Location Permission
            checkLocationPermission()
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

//                val markerOptions = MarkerOptions()
//
//                markerOptions.position(latLng)
//
//                markerOptions.title(location.latitude.toString() + " : " + location.longitude)

                mGoogleMap.clear()

                mGoogleMap.animateCamera(CameraUpdateFactory.newLatLng(latLng))

//                mGoogleMap.addMarker(markerOptions)
                val cameraPosition =
                    CameraPosition.Builder().target(LatLng(latLng.latitude, latLng.longitude))
                        .zoom(18f).build()
                mGoogleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
            }
        }
    }

    private fun addMarkerPhotoToMap(photoLocation: PhotoLocation) {
        val location = LatLng(photoLocation.latitude, photoLocation.longitude)
        mGoogleMap.addMarker(MarkerOptions().position(location).title(photoLocation.address))
        mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15f))
    }


    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION
                )
            ) {
                AlertDialog.Builder(requireContext()).setTitle("Location Permission Needed")
                    .setMessage("This app needs the Location permission, please accept to use location functionality")
                    .setPositiveButton(
                        "OK"
                    ) { _, i ->
                        ActivityCompat.requestPermissions(
                            requireActivity(),
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                            MY_PERMISSIONS_REQUEST_LOCATION
                        )
                    }.create().show()
            } else {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    MY_PERMISSIONS_REQUEST_LOCATION
                )
            }
        }
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

    private fun getPhotoLocation(photoFile: File): PhotoLocation? {
        val exifInterface = ExifInterface(photoFile.absolutePath)
        val latLong = FloatArray(2)

        return if (exifInterface.getLatLong(latLong)) {
            val latitude = latLong[0].toDouble()
            val longitude = latLong[1].toDouble()
            val address = getAddressFromCoordinates(requireContext(), latitude, longitude)
            PhotoLocation(latitude, longitude, address)
        } else {
            null
        }
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
                // Додаткові деталі адреси:
                // val city = address.locality
                // val state = address.adminArea
                // val country = address.countryName
                // val postalCode = address.postalCode
                // ...
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return addressText
    }

//    companion object {
//        private const val TAG = "CameraXTest"
//        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
//        const val REQUEST_IMAGE_CAPTURE = 1
//        private val REQUIRED_PERMISSIONS =
//            mutableListOf(
//                Manifest.permission.CAMERA,
//                Manifest.permission.RECORD_AUDIO
//            ).apply {
//                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
//                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
//                }
//            }.toTypedArray()
//    }
}
