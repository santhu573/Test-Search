package com.example.here_search;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.Toast;

public class MainActivity extends Activity {

	private String Searchkey;
	private int Screenwidth = 0;
	private int Screenheight = 0;
	private int DownloadedbitmapCount = 0;
	private int MAX_BITMAPS = 64;

	private ArrayList<Object> GsImages;
	private File cacheDir;
	Context ctx;
	ArrayList<Bitmap> Bitmapcache = new ArrayList<Bitmap>();
	ViewTreeObserver.OnScrollChangedListener scrollchangelistener;
	ScrollView scrollView = null;
	private TableLayout ImageviewContainer;
	private static Database db;
	ArrayList<String> suggestionlist= new ArrayList<String>();
	public AutoCompleteTextView searchView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		ctx = getApplicationContext();
		ImageviewContainer = (TableLayout) findViewById(R.id.maincontainer);
		Initiliazeview();
		db = new Database(MainActivity.this, "Here");
		db.createprojecttable();
		//update history using SQL Database
		updatehistory();
		final Button Searchimages  = (Button)findViewById(R.id.btnSearch);
		 Searchimages.setOnClickListener(new View.OnClickListener() {
			    public void onClick(View v) {
			    	String query = searchView.getText().toString();
			    	if(! suggestionlist.contains(query)){
			    		suggestionlist.add(query);
			    		 Initiliazeview();
	            		db.addValue(Database.TABLE_HISTORY, query, query);
	            	}
			    	 InputMethodManager imm = (InputMethodManager)getSystemService(
	                	      Context.INPUT_METHOD_SERVICE);
	                	imm.hideSoftInputFromWindow(searchView.getWindowToken(), 0);
	                	searchView.clearFocus();
	            	StartSearch(query);
			    }
			});
		// To setup the ImageView dimensions independent of Screen size
		Display display = getWindowManager().getDefaultDisplay();
		Point size = new Point();
		display.getSize(size);
		Screenwidth = size.x;
		Screenheight = size.y;

		// To setup the file system to store temporary bitmaps
		filesystemsetup();
		scrollView = (ScrollView) findViewById(R.id.scroll);

	}

	public void filesystemsetup() {
		if (android.os.Environment.getExternalStorageState().equals(
				android.os.Environment.MEDIA_MOUNTED)) {
			cacheDir = new File(
					android.os.Environment.getExternalStorageDirectory(),
					ctx.getString(R.string.cache_location));
		} else {
			cacheDir = ctx.getCacheDir();
		}
		if (!cacheDir.exists()) {
			cacheDir.mkdirs();
		}
	}
	public void Initiliazeview(){
		searchView = (AutoCompleteTextView)findViewById(R.id.AutoSeach);
		final ArrayAdapter<String> SuggestionAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_dropdown_item_1line, suggestionlist);
		searchView = (AutoCompleteTextView)findViewById(R.id.AutoSeach);
		searchView.setAdapter(SuggestionAdapter); 
	}
	
	public void updatehistory(){
		SQLiteDatabase tempdb = db.getReadableDatabase();
		Cursor cursor = tempdb.query(
				Database.TABLE_HISTORY, null, null, null,
				null, null, null, null);
		cursor.moveToFirst();
        int rows = 	cursor.getCount();
        for (int i=0;i<rows;i++){
        	suggestionlist.add(cursor.getString(1));
        	cursor.moveToNext();
        }
        
		
	}

	public void enablescroll() {
		
		final ViewTreeObserver.OnScrollChangedListener onScrollChangedListener = new ViewTreeObserver.OnScrollChangedListener() {

			@Override
			public void onScrollChanged() {
				Rect scrollBounds = new Rect();
				scrollView.getHitRect(scrollBounds);
				int totalrows = ImageviewContainer.getChildCount();
				if (totalrows != 0) {
					// End of Scroll is detected with Scrollbound when it is in
					// visibility with the last child of Table Layout Container
					if (ImageviewContainer.getChildAt(totalrows - 1)
							.getLocalVisibleRect(scrollBounds)
							&& DownloadedbitmapCount < MAX_BITMAPS)
					{
						Log.d("scroll ends here",
								"downloaded count"
										+ String.valueOf(DownloadedbitmapCount)); 
						Bitmapcache.clear();
						new getImagesTask().execute();
					}
				}
			}
		};

		scrollView.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				ViewTreeObserver observer = scrollView.getViewTreeObserver();
				observer.addOnScrollChangedListener(onScrollChangedListener);
				return false;
			}
		});
	}

	
	public void disablescroll() {
		scrollView.setOnTouchListener(new View.OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				//Scroll Touch is disabled
				return true;
			}
		});
	}

	private void StartSearch(String query) {
		// This method clears off everything to start the new search
		Searchkey = query;
		DownloadedbitmapCount = 0;
		Log.d("Search", "Started");
		Toast.makeText(ctx, "Search Started", Toast.LENGTH_SHORT).show();
		ImageviewContainer.removeAllViews();
		clearCache();
		Searchkey = Uri.encode(Searchkey);
		new getImagesTask().execute();
	}

	public class getImagesTask extends AsyncTask<Void, Void, Void> {
		JSONObject json;
		ProgressDialog dialog;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			disablescroll();
			dialog = ProgressDialog.show(MainActivity.this, "",
					"Please wait...");
		}

		@Override
		protected Void doInBackground(Void... params) {
			
			if (DownloadedbitmapCount < MAX_BITMAPS)
			{
				// Request to get the JSON content from google image search api
				json = loadJson(json, 8);

				// Download the Thumbnails bitmaps using the URL's obtained from
				// JSON Array
				downloadImages(json);

				// Ninth image is downloaded separately as google image api
				// allows maximum of 8 Images at a time
				downloadNinthImage();
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			// TODO Auto-generated method stub
			super.onPostExecute(result);

			if (dialog.isShowing()) {
				dialog.dismiss();
			}

			// Loading the Bitmaps into the TableRows as and then TableLayout
			imageLoading(Bitmapcache);
			Bitmapcache.clear();
			// This enables to detect the end of scroll view to load more images
			enablescroll();
		}
	}

	public void downloadNinthImage() {
		JSONObject json = null;
		json = loadJson(json, 1);
		downloadImages(json);
	}

	public JSONObject loadJson(JSONObject json, int bitcount) {
		// This method obtains the json object from google
		URL url;
		try {
			url = new URL(
					"https://ajax.googleapis.com/ajax/services/search/images?"
							+ "v=1.0&q=" + Searchkey + "&rsz="
							+ String.valueOf(bitcount) + "&&start="
							+ String.valueOf(DownloadedbitmapCount));

			URLConnection connection = url.openConnection();

			String line;
			StringBuilder builder = new StringBuilder();
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					connection.getInputStream()));
			while ((line = reader.readLine()) != null) {
				builder.append(line);
			}

			json = new JSONObject(builder.toString());
		} catch (MalformedURLException e) {

			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}

		return json;
	}

	public void downloadImages(JSONObject json) {

		JSONObject responseObject;
		try {
			responseObject = json.getJSONObject("responseData");
			JSONArray resultArray = responseObject.getJSONArray("results");
			GsImages = getImageList(resultArray);
			for (int i = 0; i < GsImages.size(); i++) {
				Bitmap temp = null;
				GoogleImageBean imageBean = (GoogleImageBean) GsImages.get(i);
				String ThumbImageurl = imageBean.getThumbUrl();
				// This method downloads Images from google using URLs
				temp = getBitmap(ThumbImageurl);

				Bitmapcache.add(temp);
				DownloadedbitmapCount++;
			}

		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void imageLoading(ArrayList<Bitmap> bitmapcache) {
		// This method takes the Bitmap Images and distributes them in the Table
		// layout in 3 Columns
		int totalsize = bitmapcache.size();
		Log.d("Imageloading", "bitmapcache" + String.valueOf(totalsize));
		int currentbitmap = 0;
		int ImageviewWidth = (int) (Screenwidth * 0.3);
		int ImageviewHeight = (int) (Screenheight * 0.3);
		int paddingPixel = 5;
		float density = getApplicationContext().getResources()
				.getDisplayMetrics().density;
		int paddingDp = (int) (paddingPixel * density);

		Log.d("Image Loading Size", Integer.toString(totalsize));
		while (currentbitmap < totalsize) {

			TableRow localTablerow = new TableRow(getApplicationContext());
			for (int j = 0; j < 3; j++) {
				Log.d("Image Loading column", Integer.toString(j));
				if (currentbitmap < totalsize) {
					ImageView GSImageView = new ImageView(
							getApplicationContext());
					GSImageView.setPadding(paddingDp, paddingDp, paddingDp,
							paddingDp);
					Bitmap lBitmap = bitmapcache.get(currentbitmap);
					GSImageView.setImageBitmap(lBitmap);
					localTablerow.addView(GSImageView, ImageviewWidth,
							ImageviewHeight);
					currentbitmap++;
				} else {
					break;
				}
			}
			ImageviewContainer.addView(localTablerow);

		}
	}

	public ArrayList<Object> getImageList(JSONArray resultArray) {
		ArrayList<Object> listImages = new ArrayList<Object>();
		GoogleImageBean bean;

		try {
			for (int i = 0; i < resultArray.length(); i++) {
				JSONObject obj;
				obj = resultArray.getJSONObject(i);
				bean = new GoogleImageBean();
				bean.setThumbUrl(obj.getString("tbUrl"));
				listImages.add(bean);

			}
			return listImages;
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	private Bitmap getBitmap(String url) {
		// This method downloads the Images from the URL

		String filename = String.valueOf(url.hashCode());
		File f = new File(cacheDir, filename);

		Bitmap b = decodeFile(f);
		if (b != null)
			return b;

		try {
			Bitmap bitmap = null;
			InputStream is = new URL(url).openStream();
			OutputStream os = new FileOutputStream(f);
			CopyStream(is, os);
			os.close();
			bitmap = decodeFile(f);
			return bitmap;
		} catch (MalformedURLException e) {
			Bitmap bookDefaultIcon = BitmapFactory.decodeResource(
					ctx.getResources(), R.drawable.ic_launcher);
			return bookDefaultIcon;
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}
	public static void CopyStream(InputStream is, OutputStream os) {
		final int buffer_size = 1024;
		try {
			byte[] bytes = new byte[buffer_size];
			for (;;) {
				int count = is.read(bytes, 0, buffer_size);
				if (count == -1)
					break;
				os.write(bytes, 0, count);
			}
		} catch (Exception ex) {
		}
	}


	private Bitmap decodeFile(File f) {
		try {
			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			BitmapFactory.decodeStream(new FileInputStream(f), null, o);

			final int REQUIRED_SIZE = 70;
			int width_tmp = o.outWidth, height_tmp = o.outHeight;
			int scale = 1;
			while (true) {
				if (width_tmp / 2 < REQUIRED_SIZE
						|| height_tmp / 2 < REQUIRED_SIZE)
					break;
				width_tmp /= 2;
				height_tmp /= 2;
				scale++;
			}

			BitmapFactory.Options o2 = new BitmapFactory.Options();
			o2.inSampleSize = scale;
			return BitmapFactory.decodeStream(new FileInputStream(f), null, o2);
		} catch (FileNotFoundException e) {
		}
		return null;
	}

	public void clearCache() {
		Bitmapcache.clear();
		File[] files = cacheDir.listFiles();
		for (File f : files)
			f.delete();
	}

	@Override
	    public void onStop() {
	        super.onStop();
	        clearCache();
	    }
}
