package com.example.socialauthenticationdemo;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.conf.ConfigurationBuilder;

import com.example.socialauthenticationdemo.R;
import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.Session.Builder;
import com.facebook.Session.OpenRequest;
import com.facebook.SessionState;
import com.facebook.model.GraphUser;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	//twitter
	private static final String CALLBACK_URL ="bytefly-android:///";
	private RequestToken mReqToken;
	private Twitter mTwitter;
	
	//facebook
	private Session.StatusCallback statusCallback = new SessionStatusCallback();

	//preferences
	public static final String PREFERNCES_NAME="BYTEFLY_DEMO_PREFERENCES";
	
	public static final String USER_EMAIL="user_email";
	public static final String USER_FIRST_NAME="first_name";
	public static final String USER_LAST_NAME="last_name";
	
	public static final String TWITTER_ACCESS_TOKEN="twitter_access_token";
	public static final String TWITTER_ACCESS_TOKEN_SECRET="twitter_access_token_secret";
	public static final String TWITTER_USER_ID="twitter_id";
	
	//variables
	private String firstName;
	private String lastName;

	//views
	private Button facebookLogin;
	private Button twitterLogin;
	private Button easterEgg;
	private WebView webView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		facebookLogin=(Button) findViewById(R.id.facebook_sign_in);
		twitterLogin=(Button) findViewById(R.id.twitter_sign_in);
		webView = (WebView) findViewById(R.id.twitter_webview);
		ConfigurationBuilder cb = new ConfigurationBuilder();
		cb.setUseSSL(true);
		mTwitter = new TwitterFactory().getInstance();
		mTwitter.setOAuthConsumer(getString(R.string.twitter_consumer_key), getString(R.string.twitter_consumer_secret));
		
		try {
			PackageInfo info = getPackageManager().getPackageInfo(
					"com.example.socialauthenticationdemo", PackageManager.GET_SIGNATURES);
			for (Signature signature : info.signatures) 
			{
				MessageDigest md = MessageDigest.getInstance("SHA");
				md.update(signature.toByteArray());
				//Unsafe to leave this in plain text in log messages for release
				Log.d("KeyHash:", Base64.encodeToString(md.digest(), Base64.DEFAULT));
			}
		} catch (NameNotFoundException e) {
		} catch (NoSuchAlgorithmException e) {
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		firstName=getStringPreference(USER_FIRST_NAME);
		lastName=getStringPreference(USER_LAST_NAME);
		if(firstName!=null || lastName!=null){
			easterEgg = (Button)findViewById(R.id.easter_egg);
			easterEgg.setVisibility(View.VISIBLE);
		}
	}

	public void savePreference(String preferenceName, String value){
		SharedPreferences preferences = getSharedPreferences(MainActivity.PREFERNCES_NAME, MODE_PRIVATE);
		SharedPreferences.Editor editor= preferences.edit();
		editor.putString(preferenceName, value);
		editor.commit();
	}
	public String getStringPreference(String preferenceName){
		SharedPreferences preferences = getSharedPreferences(MainActivity.PREFERNCES_NAME, MODE_PRIVATE);
		return preferences.getString(preferenceName, null);
	}

	public void twitterLogin(View v){
		
		if (twitterIsAuthorized()) {
			new TwitterUserRequestTask().execute(CALLBACK_URL);
		}else {
			facebookLogin.setVisibility(View.GONE);
			twitterLogin.setVisibility(View.GONE);
			new TwitterLoginTask().execute(CALLBACK_URL);
		}

	}
	
	

	public void facebookLogin(View v) {
		if (Session.getActiveSession() != null && Session.getActiveSession().isOpened()) {
			Session.getActiveSession().closeAndClearTokenInformation();
		}

		Session.openActiveSession(this, true, statusCallback);

		/*List<String> permissions = new ArrayList<String>();
		permissions.add("email");
		OpenRequest openRequest = new Session.OpenRequest(this);
		openRequest.setPermissions(permissions);
		openRequest.setCallback(statusCallback);
		
		Session session = new Builder(this).build();
		Session.setActiveSession(session);
		session.openForRead(openRequest);*/
	}


	private class SessionStatusCallback implements Session.StatusCallback {

		@Override
		public void call(Session session, SessionState state, Exception exception) {
			switch (state) {
			case OPENED:
			case OPENED_TOKEN_UPDATED:
				if (exception == null) {
					if (session.isOpened()) {

						Request.executeMeRequestAsync(session, new Request.GraphUserCallback() {

							@Override
							public void onCompleted(GraphUser user, Response response) {
								if (user != null) {
									savePreference(USER_FIRST_NAME,user.getFirstName());
									savePreference(USER_LAST_NAME,user.getLastName());
									Toast.makeText(MainActivity.this, "Facebook Login Successful", Toast.LENGTH_LONG).show();

								} else {
									// TODO : Error retrieving user information
									Toast.makeText(MainActivity.this, "Error retrieving user information", Toast.LENGTH_LONG).show();
								}
							}
						});

					} else {
						// TODO : Error opening facebook session
						Toast.makeText(MainActivity.this, "Error opening facebook session", Toast.LENGTH_LONG).show();
					}

				} else {
					// TODO : Facebook login exception
					Toast.makeText(MainActivity.this, exception.getMessage(), Toast.LENGTH_LONG).show();
				}
				break;

			case CLOSED:
			case CLOSED_LOGIN_FAILED:
				if (exception != null) {
					// TODO : Facebook login exception
					Toast.makeText(MainActivity.this, exception.getMessage(), Toast.LENGTH_LONG).show();
				}
				break;

			default:
				break;
			}
		}
	}
	
	
	private boolean twitterIsAuthorized() {
		String accessToken = getStringPreference(TWITTER_ACCESS_TOKEN);
		return accessToken != null && accessToken.length() > 0;
	}
	
	private class TwitterLoginTask extends AsyncTask<String, Void, Boolean> {

		@Override
		protected Boolean doInBackground(String... params) {
			try {
				
				mReqToken = mTwitter.getOAuthRequestToken(params[0]);
				return true;
			} catch (TwitterException e) {
				e.printStackTrace();
				return false;
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		}

		@Override
		protected void onPostExecute(Boolean loggedIn) {
			if (loggedIn) {
				webView.setVisibility(View.VISIBLE);
				webView.setWebViewClient(new WebViewClient() {
					@Override
					public boolean shouldOverrideUrlLoading(WebView view, String url) {
						if (url.contains("denied")) {
							
							webView.setVisibility(View.GONE);
							facebookLogin.setVisibility(View.VISIBLE);
							twitterLogin.setVisibility(View.VISIBLE);
							return false;
						} else if (url.contains(CALLBACK_URL)) {
							Uri uri = Uri.parse(url);
							String oauthVerifier = uri.getQueryParameter("oauth_verifier");
							new TwitterAuthorizeApp().execute(oauthVerifier);
							return true;
						} else 
							return false;
					}
				});
				webView.loadUrl(mReqToken.getAuthenticationURL());
			}
			else {
				webView.setVisibility(View.GONE);
				facebookLogin.setVisibility(View.VISIBLE);
				twitterLogin.setVisibility(View.VISIBLE);
				Toast.makeText(MainActivity.this, "Twitter login failure.", Toast.LENGTH_SHORT).show();
			}
		}
	}
	
	
	private class TwitterAuthorizeApp extends AsyncTask<String, Void, Boolean> {

		@Override
		protected Boolean doInBackground(String... params) {
			try {
				AccessToken at = mTwitter.getOAuthAccessToken(mReqToken, params[0]);
				mTwitter.setOAuthAccessToken(at);
				savePreference(TWITTER_ACCESS_TOKEN, at.getToken());
				savePreference(TWITTER_ACCESS_TOKEN_SECRET, at.getTokenSecret());
				savePreference(TWITTER_USER_ID, Long.toString(at.getUserId()));
				return true;
			} catch (TwitterException e) {

				return false;
			}
		}

		@Override
		protected void onPostExecute(Boolean success) {
			webView.setVisibility(View.GONE);
			facebookLogin.setVisibility(View.VISIBLE);
			twitterLogin.setVisibility(View.VISIBLE);
			if (success) {
				new TwitterUserRequestTask().execute(CALLBACK_URL);
			}
			else {
				Toast.makeText(MainActivity.this, "Twitter login failure", Toast.LENGTH_SHORT).show();
			}
		}
	}
	
	private class TwitterUserRequestTask extends AsyncTask<String, Void, Boolean> {

		@Override
		protected Boolean doInBackground(String... params) {
			try {	
				if (mReqToken == null) {
					String temp = getStringPreference(TWITTER_USER_ID);
					long userId;
					if(temp==null)
						userId=0;
					else
						userId = Long.parseLong(temp);
					
					if (userId > 0) {
						String accessToken = getStringPreference(TWITTER_ACCESS_TOKEN);
						String accessTokenSecret = getStringPreference(TWITTER_ACCESS_TOKEN_SECRET);
						AccessToken at = new AccessToken(accessToken, accessTokenSecret);
						ConfigurationBuilder builder = new ConfigurationBuilder();
						
						
							builder.setOAuthConsumerKey(getString(R.string.twitter_consumer_key));
							builder.setOAuthConsumerSecret(getString(R.string.twitter_consumer_secret));
						
						
						Twitter twitter = new TwitterFactory(builder.build()).getInstance(at);
						User user = twitter.showUser(userId);
						if (user != null) {
							savePreference(USER_FIRST_NAME,user.getName());
							
						}
					}
				} else {
					long userId = mTwitter.getOAuthAccessToken().getUserId();
					if (userId > 0) {
						User user = mTwitter.showUser(userId);
						if (user != null) {
							savePreference(USER_FIRST_NAME,user.getName());
						}
					}
				}
				return true;
			}
			catch (TwitterException e) {
				Log.e("TwitterException", e.getMessage());
				return false;
			}
		}

		@Override
		protected void onPostExecute(Boolean success) {
			Log.d("OnPostExecute","OnPostExecute");
			if(success)
				Toast.makeText(MainActivity.this, "Twitter login successful!", Toast.LENGTH_SHORT).show();
			else
				Toast.makeText(MainActivity.this, "Twitter login failure!", Toast.LENGTH_SHORT).show();
		}
	}
	
	public void easterEgg(View v){
		TextView text= (TextView) findViewById(R.id.easter_egg_textView);
		firstName=getStringPreference(USER_FIRST_NAME);
		lastName=getStringPreference(USER_LAST_NAME);
		if(firstName==null)
			firstName="";
		else if(lastName==null)
			lastName="";
		text.setText(firstName + " " +lastName);
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		Session.getActiveSession().onActivityResult(this, requestCode, resultCode, data);
	}
	
	
	

}
