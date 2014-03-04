package com.ttoivanen.flappy;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.PolygonShape;

import org.andengine.audio.music.Music;
import org.andengine.audio.music.MusicFactory;
import org.andengine.engine.camera.Camera;
import org.andengine.engine.options.EngineOptions;
import org.andengine.engine.options.ScreenOrientation;
import org.andengine.engine.options.resolutionpolicy.RatioResolutionPolicy;
import org.andengine.entity.primitive.Rectangle;
import org.andengine.entity.scene.IOnSceneTouchListener;
import org.andengine.entity.scene.Scene;
import org.andengine.entity.scene.background.Background;
import org.andengine.entity.sprite.Sprite;
import org.andengine.extension.physics.box2d.PhysicsConnector;
import org.andengine.extension.physics.box2d.PhysicsFactory;
import org.andengine.extension.physics.box2d.PhysicsWorld;
import org.andengine.input.touch.TouchEvent;
import org.andengine.opengl.texture.TextureOptions;
import org.andengine.opengl.texture.atlas.bitmap.BitmapTextureAtlas;
import org.andengine.opengl.texture.atlas.bitmap.BitmapTextureAtlasTextureRegionFactory;
import org.andengine.opengl.texture.region.ITextureRegion;
import org.andengine.ui.activity.SimpleBaseGameActivity;

import java.io.IOException;
import java.util.Random;

import static org.andengine.extension.physics.box2d.util.constants.PhysicsConstants.PIXEL_TO_METER_RATIO_DEFAULT;

// TODO: Dispose of old obstacles
// TODO: Tweak flying mechanics & difficulty
// TODO: Advanced graphics and audio
// TODO: Splash screen
// TODO: Read polygons from XML/json

public class MainActivity extends SimpleBaseGameActivity implements IOnSceneTouchListener {

    // Declare some variables for global use
    Music sound;
    Camera cam;
    Scene scn;

    int dispHeight;
    int dispWidth;
    int obstacleSetsMade = 0;
    int score = 0;
    int highScore = 0;

    boolean gameOver = false;

    PhysicsWorld physicsWorld;

    Vector2 gravity;

    Body body1;
    Body body2;
    Body body3;
    Sprite birdSprite;
    Rectangle ob1;
    Rectangle ob2;

    private ITextureRegion birdTextureRegion;

    private static final String DEBUG_TAG = "Debug that shit"; // Let the debugging commence :-D

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Start game by showing the dialog
        FragmentManager fMan = getFragmentManager();
        DialogFragment newFragment = new introDialogFragment();
        newFragment.show(fMan, "lol");
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {

            View decorView = this.getWindow().getDecorView();

            // Get device model and Android version
            // Activate immersive mode accordingly

            if (Build.MODEL.equals("Nexus 4") || Build.MODEL.equals("Nexus 7") || Build.MODEL.equals("Nexus 5")) {

                if (Build.VERSION.SDK_INT >= 19) {
                    decorView.setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                }
            }
        }
    }

    @Override
    protected void onCreateResources() throws IOException {
        loadStuff();
    }

    @Override
    protected Scene onCreateScene() {

        // Set up our scene
        // Also begin setting up physics stuff

        scn = new Scene();
        scn.setBackground(new Background(0, 0, 0));
        scn.setOnSceneTouchListener(this);

        initPhysics();

        return scn;
    }

    @Override
    public EngineOptions onCreateEngineOptions() {

        // Here we set up our engine

        DisplayMetrics metrics = getResources().getDisplayMetrics();

        int width;
        int height;

        // Our code reads the resolution before the immersive mode
        // has been activated resulting in too small value
        // We must set the resolution by hand for select devices
        if (Build.MODEL.equals("Nexus 4")) {
            height = 1280;
        } else if (Build.MODEL.equals("Nexus 5")) {
            height = 1920;
        } else if (Build.MODEL.equals("Nexus 7")) {
            height = 1920;
        } else height = metrics.heightPixels;
        width = metrics.widthPixels;

        // Time to set dimensions, screen rotation, audio, etc.
        dispHeight = height;
        dispWidth = width;

        cam = new Camera(0,0, width, height);

        final EngineOptions engineOptions = new EngineOptions(true,
                ScreenOrientation.PORTRAIT_FIXED, new RatioResolutionPolicy(width, height), cam);
        engineOptions.getAudioOptions().setNeedsSound(true);
        engineOptions.getAudioOptions().setNeedsMusic(true);

        return engineOptions;

    }

    @Override
    public boolean onSceneTouchEvent(Scene pScene, final TouchEvent pSceneTouchEvent) {

        // We want to activate something when user's finger
        // touches down

        if (pSceneTouchEvent.isActionDown()) {

            // Check whether the game has ended or not
            if (!gameOver) {

                // Give the player model a nice boost upwards
                body3.setLinearVelocity(body3.getLinearVelocity().x, 15.0f);
                sound.play();
            }

            // Check on every tap whether new obstacles need to be rendered or not
            if ((int) (((birdSprite.getX() - 750)/300) + 1) >= (3 + (obstacleSetsMade - 1)*5)) {
                createObstacles();
            }
        }
        return false;
    }

    private void loadStuff() {

        // Load audio and/or graphics
        // for timely access
        try {
            sound = MusicFactory.createMusicFromAsset(mEngine.getMusicManager(), this,
                    "audio/derp.ogg");
            sound.setVolume(15);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Load top score from settings storage
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        highScore = prefs.getInt("storedHighScore", 0);

        // Load graphics
        BitmapTextureAtlasTextureRegionFactory.setAssetBasePath("graphics/");
        BitmapTextureAtlas bird = new BitmapTextureAtlas(getTextureManager(), 256, 256, TextureOptions.DEFAULT);
        birdTextureRegion = BitmapTextureAtlasTextureRegionFactory.createFromAsset(bird, this, "birdy.png", 0, 0);
        bird.load();

    }

    private void initPhysics() {

        // Set up physics world with gravity
        // and proceed to creating bodies

        gravity = new Vector2(0f, 0f);
        physicsWorld = new PhysicsWorld(gravity, false);
        scn.registerUpdateHandler(physicsWorld);

        physicsWorld.setContactListener(createContactListener());

        createBodies();
    }

    private void createBodies() {

        // Here we generate a static ground and ceiling with infinite enough length ...

        final Body body1;
        final Body body2;

        birdSprite = new Sprite(256, 256, birdTextureRegion, mEngine.getVertexBufferObjectManager());
        birdSprite.setSize(dispHeight/10, dispHeight/10);
        birdSprite.setPosition(dispWidth/2, dispHeight/2);

        FixtureDef fixDef = PhysicsFactory.createFixtureDef(1.0f, 0.15f, 1.0f);

        // Defining a rectangle graphic
        // position coordinate x, position coordinate y, dimension x, dimension y, vertexBufferObjectManager
        Rectangle ground = new Rectangle(0, -dispHeight/3, dispWidth*9999, dispHeight,
                this.mEngine.getVertexBufferObjectManager());
        ground.setColor(0, 0, 1);
        body1 = PhysicsFactory.createBoxBody(physicsWorld, ground, BodyDef.BodyType.StaticBody, fixDef);
        body1.setUserData("ground");
        this.scn.attachChild(ground);

        Rectangle top = new Rectangle(0, dispHeight+(dispHeight/3), dispWidth*9999, dispHeight,
                this.mEngine.getVertexBufferObjectManager());
        top.setColor(0, 0, 1);
        body2 = PhysicsFactory.createBoxBody(physicsWorld, top, BodyDef.BodyType.StaticBody, fixDef);
        body2.setUserData("top");
        this.scn.attachChild(top);

        // ... and the dynamic player model.
        final float width = birdSprite.getWidth() / PIXEL_TO_METER_RATIO_DEFAULT;
        final float height = birdSprite.getHeight() / PIXEL_TO_METER_RATIO_DEFAULT;

        body3 = polygonBody("bird", width, height, fixDef, birdSprite);
        body3.setUserData("player");
        physicsWorld.registerPhysicsConnector(new PhysicsConnector(birdSprite, body3, true, true));
        this.scn.attachChild(birdSprite);

        // We want the camera to follow player
        cam.setChaseEntity(birdSprite);

        // We can use physics debug if we want like so:
        /*DebugRenderer debug = new DebugRenderer(physicsWorld, getVertexBufferObjectManager());
        scn.attachChild(debug);*/

        createObstacles();

    }

    Body polygonBody(String id, float width, float height, FixtureDef fixDef, Sprite sprite) {

        Body body;

        if (id.equals("bird")) {
            final BodyDef bodyDef = new BodyDef();
            bodyDef.type = BodyDef.BodyType.DynamicBody;
            bodyDef.position.x = sprite.getX() / PIXEL_TO_METER_RATIO_DEFAULT;
            bodyDef.position.y = sprite.getY() / PIXEL_TO_METER_RATIO_DEFAULT;
            body = physicsWorld.createBody(bodyDef);
            final PolygonShape polyShape = new PolygonShape();

            final Vector2[] vertices1 = {
                    new Vector2(-0.14138f*width, +0.01519f*height),
                    new Vector2(-0.05729f*width, +0.01790f*height),
                    new Vector2(+0.12988f*width, +0.10200f*height),
                    new Vector2(-0.27702f*width, +0.29460f*height),
                    new Vector2(-0.21463f*width, +0.14811f*height)
            };

            polyShape.set(vertices1);
            fixDef.shape = polyShape;
            body.createFixture(fixDef);
            polyShape.dispose();

            final Vector2[] vertices2 = {
                    new Vector2(+0.12988f*width, +0.09657f*height),
                    new Vector2(+0.10004f*width, +0.19151f*height),
                    new Vector2(-0.13053f*width, +0.32715f*height),
                    new Vector2(-0.10069f*width, +0.19694f*height)
            };

            final PolygonShape polyShape2 = new PolygonShape();
            polyShape2.set(vertices2);
            fixDef.shape = polyShape2;
            body.createFixture(fixDef);
            polyShape2.dispose();

            final Vector2[] vertices3 = {
                    new Vector2(-0.12782f*width, -0.35373f*height),
                    new Vector2(+0.26280f*width, -0.10959f*height),
                    new Vector2(+0.37945f*width, +0.11013f*height),
                    new Vector2(+0.37674f*width, +0.18609f*height),
                    new Vector2(+0.31977f*width, +0.27018f*height),
                    new Vector2(+0.20041f*width, +0.28103f*height),
                    new Vector2(+0.09462f*width, +0.19965f*height)
            };

            final PolygonShape polyShape3 = new PolygonShape();
            polyShape3.set(vertices3);
            fixDef.shape = polyShape3;
            body.createFixture(fixDef);
            polyShape3.dispose();

            final Vector2[] vertices4 = {
                    new Vector2(-0.29872f*width, -0.14757f*height),
                    new Vector2(-0.05953f*width, -0.24624f*height),
                    new Vector2(+0.00510f*width, +0.06131f*height),
                    new Vector2(-0.48318f*width, +0.01519f*height)
            };

            final PolygonShape polyShape4 = new PolygonShape();
            polyShape4.set(vertices4);
            fixDef.shape = polyShape4;
            body.createFixture(fixDef);
            polyShape4.dispose();

            final Vector2[] vertices5 = {
                    new Vector2(+0.32520f*width, +0.07758f*height),
                    new Vector2(+0.45269f*width, +0.07216f*height),
                    new Vector2(+0.48524f*width, +0.18338f*height),
                    new Vector2(+0.37402f*width, +0.18609f*height)
            };

            final PolygonShape polyShape5 = new PolygonShape();
            polyShape5.set(vertices5);
            fixDef.shape = polyShape5;
            body.createFixture(fixDef);
            polyShape5.dispose();

        } else body = null;
        return body;
    }

    private void createObstacles() {

        // Need something to make this game infinitely irritating.

        int obstacleHeight = dispHeight/3;
        int obstacleWidth = dispWidth/8;
        int obstacleCount = 0;
        int width = ((int) birdSprite.getWidth()*2); // X-axis space between obstacles
        int height = dispHeight/5 ; // Y-axis space between obstacles

        FixtureDef fixDef = PhysicsFactory.createFixtureDef(1.0f, 0.15f, 1.0f);

        // We want to create obstacles according to the player's progress

        while (obstacleCount < 5) {

            // Now we shall randomize stuffs
            // Will the player's path descend (0) or ascend (1)
            Random rn = new Random();
            int path = rn.nextInt(1);

            // How much the path changes
            int movementIncrement = 0;
            if (obstacleCount != 0) movementIncrement = rn.nextInt(dispHeight/120);
            if (path == 0) {
                movementIncrement = -(movementIncrement)*20;
            } else movementIncrement = movementIncrement*20;

            // Make the actual bodies

            int obstacleMultiplier;
            if (obstacleSetsMade != 0) {
                obstacleMultiplier = obstacleCount + obstacleSetsMade*5;
            } else obstacleMultiplier = obstacleCount;

            ob1 = new Rectangle(dispWidth + width*obstacleMultiplier, (dispHeight/12)+(obstacleHeight/2) + movementIncrement,
                    obstacleWidth, obstacleHeight, this.mEngine.getVertexBufferObjectManager());
            ob1.setColor(0, 0, 1);
            body1 = PhysicsFactory.createBoxBody(physicsWorld, ob1, BodyDef.BodyType.StaticBody, fixDef);
            body1.setUserData("ob1");
            this.scn.attachChild(ob1);

            ob2 = new Rectangle(dispWidth + width*obstacleMultiplier, ((ob1.getY())+obstacleHeight+height)+500, obstacleWidth,
                    obstacleHeight+1000, this.mEngine.getVertexBufferObjectManager());
            ob2.setColor(0, 0, 1);
            body2 = PhysicsFactory.createBoxBody(physicsWorld, ob2, BodyDef.BodyType.StaticBody, fixDef);
            body2.setUserData("ob2");
            this.scn.attachChild(ob2);

            obstacleCount++;
        }

        obstacleSetsMade++; // 5 obstacles were generated
    }

    private ContactListener createContactListener() {
        return new ContactListener() {
            @Override
            public void beginContact(Contact contact) {
                final Fixture x1 = contact.getFixtureA();
                final Fixture x2 = contact.getFixtureB();

                // Check which entities are colliding
                // and handle accordingly


                if (x2.getBody().getUserData().equals("player")&&x1.getBody().getUserData().equals("ground")) {
                    if (!gameOver) {
                        sound.play(); // Play collision sound
                        gameOver = true; // We are done
                        scoreHandler(); // Score stuff
                    }
                }

                if (x2.getBody().getUserData().equals("ob1")&&x1.getBody().getUserData().equals("player")) {
                    if (!gameOver) {
                        sound.play();
                        gameOver = true;
                        scoreHandler();
                    }
                }

                if (x2.getBody().getUserData().equals("ob2")&&x1.getBody().getUserData().equals("player")) {
                    if (!gameOver) {
                        sound.play();
                        gameOver = true;
                        scoreHandler();
                    }
                }
            }

            @Override
            public void endContact(Contact contact)
            {

            }

            @Override
            public void preSolve(Contact contact, Manifold oldManifold) {

            }

            @Override
            public void postSolve(Contact contact, ContactImpulse impulse)
            {

            }
        };
    }

     private class showToast extends AsyncTask<Void, Void, Void> {

        protected Void doInBackground(Void... param) {
            return null;
        }

        protected void onPostExecute(Void param) {

            // Display score
            Context context = getApplicationContext();
            CharSequence text = "Score: " + score + "\n" + "High score: " + highScore;
            int duration = Toast.LENGTH_LONG;

            assert context != null;
            Toast toast = Toast.makeText(context, text, duration);
            toast.show();

        }
    }

    public class introDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {

            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setMessage(R.string.intro_message)
                    .setNeutralButton(R.string.intro_okay, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // Want to start game
                            // Activate gravity and set velocity for player
                            body3.setLinearVelocity(6.0f, 0f);
                            gravity = new Vector2(0, -35f);
                            physicsWorld.setGravity(gravity);
                        }
                    });
            return builder.create();
        }
    }

    public class reloadDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {

            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setMessage(R.string.reload_message)
                    .setNegativeButton(R.string.reload_nah, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            finish(); // Exit activity
                        }
                    })
                    .setPositiveButton(R.string.reload_yep, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            finish(); // Exit
                            startActivity(getIntent()); // and relaunch activity
                        }
                    });
            return builder.create();
        }
    }


    public void scoreHandler() {
        if (score == 0) {

            score = (int) (((birdSprite.getX() - 750)/300) + 1); // Get the score using this strange enough method

            // Write score to settings storage if it's higher than the current top score
            if (score > highScore) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt("storedHighScore", score); // value to store
                editor.commit();
                highScore = score;
            }

            new showToast().execute(); // Display scores in a toast

            // Ask the player to try again
            delayedDialog.start(); // We want to wait before the dialog appears

        }
    }

    Thread delayedDialog = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                // We do this in a separate thread to prevent UI thread
                // from locking.
                Thread.sleep(2500);
                FragmentManager fMan = getFragmentManager();
                DialogFragment newFragment = new reloadDialogFragment();
                newFragment.show(fMan, "lol");
            } catch (Exception e) {
                e.getLocalizedMessage();
            }
        }
    });
}
