package com.codebutler;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Toast;
import android.support.design.widget.FloatingActionButton;

import com.codebutler.data.CodeButlerDbContract;
import com.codebutler.data.CodeButlerDbHelper;

import android.database.DatabaseUtils;

public class MainActivity extends AppCompatActivity implements
        KeywordEntriesRecycleViewAdapter.ListItemClickHandler,
        SharedPreferences.OnSharedPreferenceChangeListener,
        LoaderManager.LoaderCallbacks<Cursor> {

    EditText mKeywordTeditText;
    Toast mToast;
    String mSearchType;

    //SQL globals
    public CodeButlerDbHelper dbHelper;
    public static final String[] KEYWORD_TABLE_ELEMENTS = {
            CodeButlerDbContract.KeywordsDbEntry._ID,
            CodeButlerDbContract.KeywordsDbEntry.COLUMN_KEYWORD,
            CodeButlerDbContract.KeywordsDbEntry.COLUMN_TYPE,
            CodeButlerDbContract.KeywordsDbEntry.COLUMN_LESSONS,
            CodeButlerDbContract.KeywordsDbEntry.COLUMN_RELEVANT_CODE,
            CodeButlerDbContract.KeywordsDbEntry.COLUMN_SOURCE
    };
    public static final int INDEX_COLUMN_KEYWORD = 1;
    public static final int INDEX_COLUMN_TYPE = 2;
    public static final int INDEX_COLUMN_LESSONS = 3;
    public static final int INDEX_COLUMN_RELEVANT_CODE = 4;
    public static final int INDEX_COLUMN_SOURCE = 5;
    private static final int ID_KEYWORD_DATABASE_LOADER = 666;

    //RecyclerView globals
    private static final int NUM_RECYCLERVIEW_LIST_ITEMS = 100;
    private KeywordEntriesRecycleViewAdapter mKeywordEntriesRecycleViewAdapter;
    private RecyclerView mKeywordEntriesRecylcleView;
    private int mPosition = RecyclerView.NO_POSITION;
    private ProgressBar mLoadingIndicator;

    //Lifecycle methods
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Finding the layouts
        mLoadingIndicator = findViewById(R.id.pb_loading_indicator);
        mKeywordEntriesRecylcleView = findViewById(R.id.keywords_list_view);
        mKeywordTeditText = findViewById(R.id.keywordEntryEditText);

        //Initialization methods
        getSearchType();
        setupSharedPreferences();

        //Preparing the RecyclerView
        mKeywordEntriesRecylcleView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        mKeywordEntriesRecylcleView.setHasFixedSize(true);
        mKeywordEntriesRecycleViewAdapter = new KeywordEntriesRecycleViewAdapter(this, this);
        mKeywordEntriesRecylcleView.setAdapter(mKeywordEntriesRecycleViewAdapter);

        showLoadingIndicatorInsteadOfRecycleView();

        //Adding the swipe to remove entry functionality
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }
            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {

                String requested_keyword = mKeywordTeditText.getText().toString();

                //Getting the list id of the element the user wants to delete
                int id = (int) viewHolder.itemView.getTag();

                //Preparing the Uri
                String stringId = Integer.toString(id);
                Uri keywordQueryUri = CodeButlerDbContract.KeywordsDbEntry.CONTENT_URI;
                keywordQueryUri = keywordQueryUri.buildUpon().appendPath(stringId).build();

                //Preparing the arguments the user wants to delete
                //String selection = CodeButlerDbContract.KeywordsDbEntry.COLUMN_KEYWORD + " = ?";
                //String[] selectionArgs = {"'" + requested_keyword + "'"};
                String selection = null;
                String[] selectionArgs = null;

                // Deletes the words that match the selection criteria
                int mRowsDeleted = 0;
                mRowsDeleted = getContentResolver().delete(keywordQueryUri, selection, selectionArgs);

//                long id = (long) viewHolder.itemView.getTag();
//
//                // Building the appropriate uri with String row id appended
//                String stringId = Long.toString(id);
//                Uri uri = CodeButlerDbContract.KeywordsDbEntry.CONTENT_URI;
//                uri = uri.buildUpon().appendPath(stringId).build();
//
//                // Deleting a single row of data using a ContentResolver
//                getContentResolver().delete(uri, null, null);

                //Restarting the loader to re-query for all tasks after a deletion
                getSupportLoaderManager().restartLoader(ID_KEYWORD_DATABASE_LOADER, null, MainActivity.this);
            }

        }).attachToRecyclerView(mKeywordEntriesRecylcleView);

        //Adding the Add Keyowrd Entry functionality in a FloatingActionButton
        FloatingActionButton fabButton = findViewById(R.id.floatingActionButton);
        fabButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EditText userKeyword = findViewById(R.id.keywordEntryEditText);
                startNewKeywordEntryActivity(userKeyword.getText().toString());
            }
        });

        initializeDatabasesForNewDatabaseVersion();
        getSupportLoaderManager().initLoader(ID_KEYWORD_DATABASE_LOADER, null, this);


    }
    @Override protected void onDestroy() {
        super.onDestroy();
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
    }
    @Override protected void onResume() {
        super.onResume();
        getSupportLoaderManager().restartLoader(ID_KEYWORD_DATABASE_LOADER, null, this);
    }

    //Options Menu methods
    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        int itemThatWasClickedId = item.getItemId();

        switch (itemThatWasClickedId) {
            case R.id.action_refresh:dbHelper = new CodeButlerDbHelper(this);
                getSupportLoaderManager().restartLoader(ID_KEYWORD_DATABASE_LOADER, null, this);
                mKeywordEntriesRecycleViewAdapter = new KeywordEntriesRecycleViewAdapter(this,  this);
                mKeywordEntriesRecylcleView.setAdapter(mKeywordEntriesRecycleViewAdapter);
                return true;
            case R.id.action_search:
                Context context = MainActivity.this;
                String textToShow = "Search clicked";
                Toast.makeText(context, textToShow, Toast.LENGTH_SHORT).show();
                return true;
            case R.id.action_add:
                EditText userKeyword = findViewById(R.id.keywordEntryEditText);
                startNewKeywordEntryActivity(userKeyword.getText().toString());
                return true;
            case R.id.action_settings:
                Intent startSettingsActivity = new Intent(this, SettingsActivity.class);
                startActivity(startSettingsActivity);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    private void startNewKeywordEntryActivity(String keyword) {
        Intent startChildActivityIntent = new Intent(MainActivity.this, NewKeywordEntryActivity.class);
        startChildActivityIntent.putExtra(Intent.EXTRA_TEXT, keyword);
        startActivity(startChildActivityIntent);
    }
    private void setupSharedPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        setShowGDCADCourse(sharedPreferences.getBoolean(getString(R.string.pref_show_GDC_AD_course_key), getResources().getBoolean(R.bool.pref_show_GDC_AD_course_default)));
        setShowNDADCourse(sharedPreferences.getBoolean(getString(R.string.pref_show_ND_AD_course_key), getResources().getBoolean(R.bool.pref_show_ND_AD_course_default)));
        setPreferredResultType(sharedPreferences.getString(getString(R.string.pref_preferred_result_key), getString(R.string.pref_preferred_result_value_java)));
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.pref_show_GDC_AD_course_key))) {
            setShowGDCADCourse(sharedPreferences.getBoolean(key, getResources().getBoolean(R.bool.pref_show_GDC_AD_course_default)));
        }
        else if (key.equals(getString(R.string.pref_show_ND_AD_course_key))) {
            setShowNDADCourse(sharedPreferences.getBoolean(key, getResources().getBoolean(R.bool.pref_show_ND_AD_course_default)));
        }

    }
    public void setShowGDCADCourse (boolean showCourse) {

    }
    public void setShowNDADCourse (boolean showCourse) {

    }
    public void setPreferredResultType(String preferred_type) {
        switch (preferred_type) {
            case "xml":
                break;
            case "java":
                break;
            case "explanations":
                break;
        }
    }

    //RecycleView methods
    @Override public void onListItemClick(int clickedItemIndex) {
        if (mToast != null) mToast.cancel();
        String toastMessage = "Item #" + clickedItemIndex + " clicked.";
        mToast = Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT);
        mToast.show();

        //startSelectedItemDetailsActivity.setData(uriForDateClicked);
        EditText userKeyword = findViewById(R.id.keywordEntryEditText);
        startNewSelectedItemDetailsActivity(userKeyword.getText().toString());
    }
    private void startNewSelectedItemDetailsActivity(String keyword) {
        Intent startChildActivityIntent = new Intent(MainActivity.this, SelectedItemDetailsActivity.class);
        startChildActivityIntent.putExtra(Intent.EXTRA_TEXT, keyword);
        //TODO Implement SharedPreferences
        startActivity(startChildActivityIntent);
    }
    private void openWebPage(String url) {
        Uri webpage = Uri.parse(url);
        Intent intent = new Intent(Intent.ACTION_VIEW, webpage);
        if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
    }
    private void showRecycleViewInsteadOfLoadingIndicator() {
        mLoadingIndicator.setVisibility(View.INVISIBLE);
        mKeywordEntriesRecylcleView.setVisibility(View.VISIBLE);
    }
    private void showLoadingIndicatorInsteadOfRecycleView() {
        mLoadingIndicator.setVisibility(View.VISIBLE);
        mKeywordEntriesRecylcleView.setVisibility(View.INVISIBLE);
    }

    //Database methods
    @Override public Loader<Cursor> onCreateLoader(int loaderId, Bundle args) {

        switch (loaderId) {
            case ID_KEYWORD_DATABASE_LOADER:
                showLoadingIndicatorInsteadOfRecycleView();
                Uri keywordQueryUri = CodeButlerDbContract.KeywordsDbEntry.CONTENT_URI;
                String requested_keyword = mKeywordTeditText.getText().toString();
                String selection = CodeButlerDbContract.KeywordsDbEntry.getSelectionForGivenKeywordsAndOperator(requested_keyword, mSearchType);
                String sortOrder = CodeButlerDbContract.KeywordsDbEntry.COLUMN_KEYWORD;
                return new CursorLoader(this, keywordQueryUri, KEYWORD_TABLE_ELEMENTS, selection, null, sortOrder);
            default:
                throw new RuntimeException("Loader Not Implemented: " + loaderId);
        }
    }
    @Override public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mKeywordEntriesRecycleViewAdapter.swapCursor(data);
        if (mPosition == RecyclerView.NO_POSITION) mPosition = 0;
        mKeywordEntriesRecylcleView.smoothScrollToPosition(mPosition);
        if (data.getCount() != 0) showRecycleViewInsteadOfLoadingIndicator();
    }
    @Override public void onLoaderReset(Loader<Cursor> loader) {
        mKeywordEntriesRecycleViewAdapter.swapCursor(null);
    }
    private void initializeDatabasesForNewDatabaseVersion() {

        //Reading the previous database version from SharedPreferences
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        int defaultValueIfSharedPrefDoesNotExist = Integer.parseInt(getResources().getString(R.string.defaultDatabaseVersion));
        long databaseVersionInSharedPreferences = sharedPref.getInt(getString(R.string.databaseVersionKey), defaultValueIfSharedPrefDoesNotExist);

        //If the database version was incremented, clear and re-initialize the database and register the new database version
        if (databaseVersionInSharedPreferences < CodeButlerDbHelper.DATABASE_VERSION) {

            //create Udacity Mapping database rows from the csv in assets
            CodeButlerDbHelper dbHelper = new CodeButlerDbHelper(this);
            dbHelper.initializeKeywordsDatabase();

            //save the current database version
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putInt(getString(R.string.databaseVersionKey), CodeButlerDbHelper.DATABASE_VERSION);
            editor.apply();
        }

        getSupportLoaderManager().restartLoader(ID_KEYWORD_DATABASE_LOADER, null, this);
    }
    private void getCursorDump() {
        Log.v("Cursor Dump", DatabaseUtils.dumpCursorToString(getContentResolver() .query(CodeButlerDbContract.KeywordsDbEntry.CONTENT_URI, KEYWORD_TABLE_ELEMENTS, null, null, null)));
    }
    //Helper methods
    private void getSearchType() {
        RadioGroup search_type = findViewById(R.id.search_type);
        mSearchType = getValueFromRadioButtons();
        search_type.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                mSearchType = getValueFromRadioButtons();
            }
        });
    }
    @NonNull private String getValueFromRadioButtons() {
        RadioGroup search_type = findViewById(R.id.search_type);
        int selectedId = search_type.getCheckedRadioButtonId();
        switch (selectedId) {
            case R.id.search_exact: return "EXACT";
            case R.id.search_and: return "AND";
            case R.id.search_or: return "OR";
            default: return  "OR";
        }
    }
}
