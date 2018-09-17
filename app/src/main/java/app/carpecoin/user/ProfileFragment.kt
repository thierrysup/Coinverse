package app.carpecoin.user

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.findNavController
import app.carpecoin.Enums
import app.carpecoin.HomeViewModel
import app.carpecoin.coin.R
import app.carpecoin.coin.databinding.FragmentProfileBinding
import app.carpecoin.firebase.FirestoreCollections.ARCHIVED_COLLECTION
import app.carpecoin.firebase.FirestoreCollections.usersCollection
import app.carpecoin.utils.Constants.ON_BACK_PRESS_DELAY_IN_MILLIS
import app.carpecoin.utils.Constants.PROFILE
import com.firebase.ui.auth.AuthUI
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import kotlinx.android.synthetic.main.fragment_profile.*
import kotlinx.android.synthetic.main.toolbar.*

private var LOG_TAG = ProfileFragment::class.java.simpleName

class ProfileFragment : Fragment() {
    private lateinit var analytics: FirebaseAnalytics
    private lateinit var binding: FragmentProfileBinding
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var user: FirebaseUser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analytics = FirebaseAnalytics.getInstance(context!!)
        analytics.setCurrentScreen(activity!!, PROFILE, null)
        homeViewModel = ViewModelProviders.of(activity!!).get(HomeViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        binding = FragmentProfileBinding.inflate(inflater, container, false)
        binding.viewmodel = homeViewModel
        user = ProfileFragmentArgs.fromBundle(arguments).user
        binding.user = user
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setToolbar()
        setClickListeners()
    }

    fun setToolbar() {
        toolbar.title = user.displayName
        (activity as AppCompatActivity).setSupportActionBar(toolbar)
        (activity as AppCompatActivity).supportActionBar!!.setDisplayHomeAsUpEnabled(true)
    }

    fun setClickListeners() {

        archivedContent.setOnClickListener { view: View ->
            val action =
                    ProfileFragmentDirections.actionProfileFragmentToArchivedContentFragment()
            action.setFeedType(Enums.FeedType.ARCHIVED.name)
            view.findNavController().navigate(R.id.action_profileFragment_to_archivedContentFragment, action.arguments)
        }

        signOut.setOnClickListener { view: View ->
            var message: Int
            if (FirebaseAuth.getInstance().currentUser != null) {
                AuthUI.getInstance()
                        .signOut(context!!)
                        .addOnCompleteListener {
                            if (it.isSuccessful) {
                                homeViewModel.user.value = null
                                message = R.string.signed_out
                                Snackbar.make(view, getString(message), Snackbar.LENGTH_SHORT).show()
                                signOut.postDelayed({
                                    activity?.onBackPressed()
                                }, ON_BACK_PRESS_DELAY_IN_MILLIS)
                            }
                            //TODO: Add retry.
                        }
            } else {
                message = R.string.already_signed_out
                Snackbar.make(view, getString(message), Snackbar.LENGTH_SHORT).show()
            }
        }
        delete.setOnClickListener { view: View ->
            var message: Int
            if (FirebaseAuth.getInstance().currentUser != null) {
                AuthUI.getInstance()
                        .delete(context!!)
                        .addOnCompleteListener {
                            if (it.isSuccessful) {
                                //TODO: Refactor to handle on server.
                                deleteCollection(usersCollection
                                        .document(homeViewModel.user.value!!.uid)
                                        .collection(ARCHIVED_COLLECTION), 20)
                                usersCollection
                                        .document(homeViewModel.user.value!!.uid)
                                        .delete()
                                        .addOnSuccessListener {
                                            homeViewModel.user.value = null
                                            message = R.string.deleted
                                            Snackbar.make(view, getString(message), Snackbar.LENGTH_SHORT).show()
                                            delete.postDelayed({
                                                activity?.onBackPressed()
                                            }, ON_BACK_PRESS_DELAY_IN_MILLIS)
                                            Log.v(LOG_TAG, String.format("Delete user success:%s", it))
                                        }.addOnFailureListener {
                                            //TODO: Add retry.
                                            Log.v(LOG_TAG, String.format("Delete user failure:%s", it))
                                        }
                            }
                        }.addOnFailureListener {
                            //TODO: Add retry.
                            Log.v(LOG_TAG, String.format("Delete user failure:%s", it))
                        }
            } else {
                message = R.string.unable_to_delete
                Snackbar.make(view, getString(message), Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteCollection(collection: CollectionReference, batchSize: Int) {
        try {
            // Retrieve a small batch of documents to avoid out-of-memory errors/
            var deleted = 0
            collection
                    .limit(batchSize.toLong())
                    .get()
                    .addOnCompleteListener {
                        for (document in it.result.documents) {
                            document.reference.delete()
                            ++deleted
                        }
                        if (deleted >= batchSize) {
                            // retrieve and delete another batch
                            deleteCollection(collection, batchSize)
                        }
                    }
        } catch (e: Exception) {
            System.err.println("Error deleting collection : " + e.message)
        }

    }
}