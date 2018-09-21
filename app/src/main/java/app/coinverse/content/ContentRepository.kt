package app.coinverse.content

import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.paging.DataSource
import app.coinverse.Enums
import app.coinverse.Enums.FeedType
import app.coinverse.Enums.FeedType.*
import app.coinverse.Enums.UserActionType
import app.coinverse.Enums.UserActionType.*
import app.coinverse.content.models.Content
import app.coinverse.content.models.UserAction
import app.coinverse.content.room.ContentDatabase
import app.coinverse.firebase.FirestoreCollections
import app.coinverse.firebase.FirestoreCollections.ARCHIVED_ACTION_COLLECTION
import app.coinverse.firebase.FirestoreCollections.ARCHIVED_COLLECTION
import app.coinverse.firebase.FirestoreCollections.ARCHIVE_COUNT
import app.coinverse.firebase.FirestoreCollections.ARCHIVE_SCORE
import app.coinverse.firebase.FirestoreCollections.CLEAR_FEED_COUNT
import app.coinverse.firebase.FirestoreCollections.INVALID_SCORE
import app.coinverse.firebase.FirestoreCollections.ORGANIZE_COUNT
import app.coinverse.firebase.FirestoreCollections.SAVED_ACTION_COLLECTION
import app.coinverse.firebase.FirestoreCollections.SAVED_COLLECTION
import app.coinverse.firebase.FirestoreCollections.SAVE_SCORE
import app.coinverse.firebase.FirestoreCollections.STARTED_ACTION_COLLECTION
import app.coinverse.firebase.FirestoreCollections.START_COUNT
import app.coinverse.firebase.FirestoreCollections.START_SCORE
import app.coinverse.firebase.FirestoreCollections.contentCollection
import app.coinverse.firebase.FirestoreCollections.usersCollection
import app.coinverse.user.models.ContentAction
import app.coinverse.utils.Constants.ARCHIVE_EVENT
import app.coinverse.utils.Constants.CLEAR_FEED_EVENT
import app.coinverse.utils.Constants.ORGANIZE_EVENT
import app.coinverse.utils.Constants.QUALITY_SCORE
import app.coinverse.utils.Constants.TIMESTAMP
import app.coinverse.utils.Constants.TIMESTAMP_PARAM
import app.coinverse.utils.Constants.USER_ID_PARAM
import app.coinverse.utils.DateAndTime.getTimeframe
import app.coinverse.utils.auth.Auth
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.*
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.*
import kotlin.collections.HashSet

class ContentRepository(application: Application) {

    private val LOG_TAG = ContentRepository::javaClass.name

    private lateinit var savedListenerRegistration: ListenerRegistration
    private lateinit var archivedListenerRegistration: ListenerRegistration
    private lateinit var contentListenerRegistration: ListenerRegistration

    private var organizedSet = HashSet<String>()
    private var analytics: FirebaseAnalytics
    private var contentFirestore: FirebaseFirestore
    private var contentDatabase: ContentDatabase

    init {
        analytics = FirebaseAnalytics.getInstance(application)
        contentFirestore = FirebaseFirestore.getInstance(FirebaseApp.getInstance(Auth.CONTENT_FIRESTORE_NAME))
        contentDatabase = ContentDatabase.getAppDatabase(application)
    }

    fun initializeMainRoomContent(isRealtime: Boolean, timeframe: Enums.Timeframe) {

        val contentDao = contentDatabase.contentDao()
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val userReference = FirestoreCollections.usersCollection.document(user.uid)
            organizedSet.clear()
            savedListenerRegistration = userReference
                    .collection(FirestoreCollections.SAVED_COLLECTION)
                    .orderBy(TIMESTAMP, Query.Direction.DESCENDING)
                    .addSnapshotListener(EventListener { value, error ->
                        error?.run {
                            Log.e(LOG_TAG, "Content EventListener Failed.", error)
                            return@EventListener
                        }
                        for (document in value!!.documentChanges) {
                            val savedContent = document.document.toObject(Content::class.java)
                            organizedSet.add(savedContent.id)
                            Thread(Runnable { run { contentDao.updateContent(savedContent) } }).start()
                        }
                    })
            archivedListenerRegistration = userReference
                    .collection(FirestoreCollections.ARCHIVED_COLLECTION)
                    .orderBy(TIMESTAMP, Query.Direction.DESCENDING)
                    .addSnapshotListener(EventListener { value, error ->
                        error?.run {
                            Log.e(LOG_TAG, "Content EventListener Failed.", error)
                            return@EventListener
                        }
                        for (document in value!!.documentChanges) {
                            val archivedContent = document.document.toObject(Content::class.java)
                            organizedSet.add(archivedContent.id)
                            Thread(Runnable { run { contentDao.updateContent(archivedContent) } }).start()
                        }
                    })
            //Logged in and realtime enabled.
            if (isRealtime) {
                contentListenerRegistration = FirestoreCollections.contentCollection
                        .orderBy(TIMESTAMP, Query.Direction.DESCENDING)
                        .whereGreaterThanOrEqualTo(TIMESTAMP, getTimeframe(timeframe))
                        .addSnapshotListener(EventListener { value, error ->
                            error?.run {
                                Log.e(LOG_TAG, "Content EventListener Failed.", error)
                                return@EventListener
                            }
                            val contentList = arrayListOf<Content?>()
                            for (document in value!!.documentChanges) {
                                val content = document.document.toObject(Content::class.java)
                                if (!organizedSet.contains(content.id)) {
                                    contentList.add(content)
                                }
                            }
                            Thread(Runnable { run { contentDao.insertContent(contentList) } }).start()
                        })
            } else { // Logged in but not realtime.
                FirestoreCollections.contentCollection
                        .orderBy(TIMESTAMP, Query.Direction.DESCENDING)
                        .whereGreaterThanOrEqualTo(TIMESTAMP, getTimeframe(timeframe))
                        .get()
                        .addOnCompleteListener {
                            val contentList = arrayListOf<Content?>()
                            for (document in it.result.documentChanges) {
                                val content = document.document.toObject(Content::class.java)
                                if (!organizedSet.contains(content.id)) {
                                    contentList.add(content)
                                }
                            }
                            Thread(Runnable { run { contentDao.insertContent(contentList) } }).start()
                        }
            }

        } else { // Looged out and thus not realtime.
            FirestoreCollections.contentCollection
                    .orderBy(TIMESTAMP, Query.Direction.DESCENDING)
                    .whereGreaterThanOrEqualTo(TIMESTAMP, getTimeframe(timeframe))
                    .get()
                    .addOnCompleteListener {
                        val contentList = arrayListOf<Content?>()
                        for (document in it.result.documents) {
                            val content = document.toObject(Content::class.java)
                            contentList.add(content)
                        }
                        Thread(Runnable { run { contentDao.insertContent(contentList) } }).start()
                    }
        }
    }

    fun initializeCategorizedRoomContent(feedType: String, userId: String) {
        var collectionType = ""
        var newFeedType = FeedType.NONE
        if (feedType == SAVED.name) {
            collectionType = SAVED_COLLECTION
            newFeedType = SAVED
        } else if (feedType == ARCHIVED.name) {
            collectionType = ARCHIVED_COLLECTION
            newFeedType = ARCHIVED
        }
        FirestoreCollections.contentCollection
                .document(userId)
                .collection(collectionType)
                .orderBy(TIMESTAMP, Query.Direction.DESCENDING)
                .addSnapshotListener(EventListener { value, error ->
                    error?.run {
                        Log.e(LOG_TAG, "Content EventListener Failed.", error)
                        return@EventListener
                    }
                    val contentList = arrayListOf<Content?>()
                    for (document in value!!.documentChanges) {
                        val content = document.document.toObject(Content::class.java)
                        content.feedType = newFeedType
                        contentList.add(content)
                    }
                    Thread(Runnable { run { contentDatabase.contentDao().insertContent(contentList) } }).start()
                })
    }

    fun getMainContent(timeframe: Date): DataSource.Factory<Int, Content> {
        return contentDatabase.contentDao().getMainContent(timeframe, MAIN)
    }

    fun getCategorizedContent(feedType: FeedType): DataSource.Factory<Int, Content> {
        return contentDatabase.contentDao().getCategorizedContent(feedType)
    }

    fun organizeContent(feedType: String, actionType: UserActionType, content: Content?,
                        user: FirebaseUser, mainFeedEmptied: Boolean) {
        val userReference = usersCollection.document(user.uid)

        // Add content to new collection.
        if (actionType == SAVE) {
            if (feedType == MAIN.name) {
                updateActionsStatusCheck(actionType, content!!, user)
            } else if (feedType == ARCHIVED.name) {
                deleteContent(userReference, ARCHIVED_COLLECTION, content)
            }
            content?.feedType = SAVED
            setContent(feedType, userReference, SAVED_COLLECTION, content, mainFeedEmptied)
        } else if (actionType == ARCHIVE) {
            if (feedType == MAIN.name) {
                updateActionsStatusCheck(actionType, content!!, user)
            } else if (feedType == SAVED.name) {
                deleteContent(userReference, SAVED_COLLECTION, content)
            }
            content?.feedType = ARCHIVED
            setContent(feedType, userReference, ARCHIVED_COLLECTION, content, mainFeedEmptied)
        }

        if (mainFeedEmptied) {
            val bundle = Bundle()
            bundle.putString(TIMESTAMP_PARAM, Date().toString())
            analytics.logEvent(CLEAR_FEED_EVENT, bundle)
            updateUserActionCounter(user.uid, CLEAR_FEED_COUNT)
        }
    }

    fun setContent(feedType: String, userReference: DocumentReference, collection: String,
                   content: Content?, mainFeedEmptied: Boolean) {
        userReference
                .collection(collection)
                .document(content!!.id)
                .set(content)
                .addOnSuccessListener {
                    Log.v(LOG_TAG, String.format("Content added to collection:%s", it))
                    logCategorizeContentAnalyticsEvent(feedType, content, mainFeedEmptied)
                }.addOnFailureListener {
                    Log.v(LOG_TAG, String.format("Content failed to be added to collection:%s", it))
                }
    }

    fun deleteContent(userReference: DocumentReference, collection: String, content: Content?) {
        userReference
                .collection(collection)
                .document(content!!.id)
                .delete()
                .addOnSuccessListener {
                    Log.v(LOG_TAG, String.format("Content deleted from to collection:%s", it))
                }.addOnFailureListener {
                    Log.v(LOG_TAG, String.format("Content failed to be deleted from collection:%s", it))
                }
    }

    fun updateActionsStatusCheck(actionType: UserActionType, content: Content, user: FirebaseUser) {
        if (actionType == ARCHIVE) {
            // Only count archived if user has not started the content.
            contentCollection.document(content.id).collection(STARTED_ACTION_COLLECTION)
                    .document(user.email!!).get().addOnSuccessListener {
                        if (!it.exists()) {
                            updateActions(actionType, content, user)
                        }
                    }.addOnFailureListener {
                        Log.w(LOG_TAG, "FAILED to get user started content.")
                    }
        } else {
            updateActions(actionType, content, user)
        }
    }

    fun updateActions(actionType: UserActionType, content: Content, user: FirebaseUser) {
        var actionCollection = ""
        var score = INVALID_SCORE
        var countType = ""

        when (actionType) {
            START -> actionCollection = STARTED_ACTION_COLLECTION
            SAVE -> actionCollection = SAVED_ACTION_COLLECTION
            ARCHIVE -> actionCollection = ARCHIVED_ACTION_COLLECTION
        }

        val contentUserActionRef = contentCollection
                .document(content.id)
                .collection(actionCollection)
                .document(user.email!!)
        contentFirestore.runTransaction(Transaction.Function<Double> { transaction ->
            val contentUserActionSnapshot = transaction.get(contentUserActionRef)
            when (actionType) {
                START -> {
                    // Only count unique starts.
                    if (!contentUserActionSnapshot.exists()) {
                        score = START_SCORE
                        countType = START_COUNT
                    } else {
                        return@Function score
                    }
                }
                SAVE -> {
                    score = SAVE_SCORE
                    countType = ORGANIZE_COUNT
                }
                ARCHIVE -> {
                    score = ARCHIVE_SCORE
                    countType = ARCHIVE_COUNT
                }
            }
            // Add user action to content's collection.
            if (score != INVALID_SCORE) {
                transaction.set(contentUserActionRef, UserAction(Date(), user.email!!))
            }
            return@Function score
        }).addOnSuccessListener { score ->
            updateContentActionCounter(content.id, countType)
            updateUserActions(user.uid, actionCollection, content, countType)
            updateQualityScore(score, content.id)
        }.addOnFailureListener { e -> Log.w(LOG_TAG, "Transaction failure.", e) }
    }

    fun updateContentActionCounter(contentId: String, counterType: String) {
        val contentRef = contentCollection.document(contentId)
        contentFirestore.runTransaction(Transaction.Function<String> { counterTransaction ->
            val contentSnapshot = counterTransaction.get(contentRef)
            val newCounter = contentSnapshot.getDouble(counterType)!! + 1.0
            counterTransaction.update(contentRef, counterType, newCounter)
            return@Function "Content counter update SUCCESS."
        }).addOnSuccessListener { status ->
            Log.w(LOG_TAG, status)
        }.addOnFailureListener { e ->
            Log.w(LOG_TAG, "Content counter update FAIL.", e)
        }
    }

    fun updateUserActions(userId: String, actionCollection: String, content: Content, countType: String) {
        usersCollection.document(userId).collection(actionCollection).document(content.id)
                .set(ContentAction(Date(), content.id, content.contentTitle, content.creator,
                        content.qualityScore)).addOnSuccessListener {
                    updateUserActionCounter(userId, countType)
                }.addOnFailureListener {
                    Log.w(LOG_TAG, "User content action update FAIL.")
                }
    }

    fun updateUserActionCounter(userId: String, counterType: String) {
        val userRef = usersCollection.document(userId)
        contentFirestore.runTransaction(Transaction.Function<String> { counterTransaction ->
            val userSnapshot = counterTransaction.get(userRef)
            val newCounter = userSnapshot.getDouble(counterType)!! + 1.0
            counterTransaction.update(userRef, counterType, newCounter)
            return@Function "User counter update SUCCESS."
        }).addOnSuccessListener { status ->
            Log.w(LOG_TAG, status)
        }.addOnFailureListener { e ->
            Log.w(LOG_TAG, "user counter update FAIL.", e)
        }
    }

    fun updateQualityScore(score: Double, contentId: String) {
        if (score != 0.0) {
            Log.d(LOG_TAG, "Transaction success: " + score)
            val contentDocRef = FirestoreCollections.contentCollection.document(contentId)
            contentFirestore.runTransaction(object : Transaction.Function<Void> {
                @Throws(FirebaseFirestoreException::class)
                override fun apply(transaction: Transaction): Void? {
                    val snapshot = transaction.get(contentDocRef)
                    val newQualityScore = snapshot.getDouble(QUALITY_SCORE)!! + score
                    transaction.update(contentDocRef, QUALITY_SCORE, newQualityScore)
                    // Success
                    return null
                }
            }).addOnSuccessListener({ Log.d(LOG_TAG, "Transaction success!") })
                    .addOnFailureListener({ e -> Log.w(LOG_TAG, "Transaction failure.", e) })
        }
    }

    fun logCategorizeContentAnalyticsEvent(feedType: String, content: Content, mainFeedEmptied: Boolean) {
        if (feedType == MAIN.name) {
            var logEvent = ""
            if (content.feedType == SAVED) {
                logEvent = ORGANIZE_EVENT
            } else if (content.feedType == ARCHIVED) {
                logEvent = ARCHIVE_EVENT
            }
            val bundle = Bundle()
            bundle.putString(FirebaseAnalytics.Param.ITEM_ID, content.id)
            bundle.putString(USER_ID_PARAM, FirebaseAuth.getInstance().currentUser?.uid)
            bundle.putString(TIMESTAMP_PARAM, Date().toString())
            analytics.logEvent(logEvent, bundle)
        }
    }

}