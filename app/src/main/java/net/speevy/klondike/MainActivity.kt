package net.speevy.klondike

import android.app.AlertDialog
import android.content.ClipData
import android.content.DialogInterface
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.ConstraintSet.*
import androidx.core.view.children
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import speevy.cardGames.AmericanCards
import speevy.cardGames.Card
import speevy.cardGames.klondike.Deck
import speevy.cardGames.klondike.Foundation
import speevy.cardGames.klondike.Klondike
import speevy.cardGames.klondike.Pile
import java.util.*
import kotlin.concurrent.timerTask
import kotlin.reflect.KProperty


private const val KLONDIKE_STATE_NAME = "KLONDIKE"
private const val DOUBLE_CLICK_TIMEOUT = 500L
private const val DRAG_TIMEOUT = 200L
private const val CARD_SHOW_PERCENT = 0.22F
private const val CARD_HIDDEN_PERCENT = 0.1F
private const val CARD_MARGIN = 4
private const val CARD_ASPECT_RATIO = 60F / 85F
private const val PILE_DECK_TO_FOUNDATIONS_GAP = 0.2 // times cardHeight
private const val APP_MENU_HEIGHT = 48 // dp
private const val FOUNDATION_HEIGHT_LIMIT= 2.5F // times cardHeight

class MainActivity : AppCompatActivity() {
    private var klondike = Klondike(AmericanCards())
    private var cardWidth = 0
    private var cardHeight = 0
    private var density : Float = 0F
    private var landscape = false
    private var width = 0
    private var height= 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val state = savedInstanceState?.getString(KLONDIKE_STATE_NAME)
        if (state != null) {
            klondike = jacksonObjectMapper().readValue(state)
        }

        calculateCardSize()

        setContentView(if (landscape) R.layout.activity_main_landscape else R.layout.activity_main)

        setContainerSize()

        setSupportActionBar(findViewById(R.id.toolbar))

        drawStatus()

        val callback: OnBackPressedCallback = object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                klondike.undo()
                drawStatus()
            }
        }

        onBackPressedDispatcher.addCallback(this, callback)

    }

    private fun setContainerSize() {
        val main: ConstraintLayout = findViewById(R.id.main)
        var totalWidth = (7 * (cardWidth + 2 * CARD_MARGIN * density)).toInt()
        Log.d("MAIN WIDTH", "$totalWidth")

        if (landscape) {
            totalWidth += (APP_MENU_HEIGHT * density).toInt()

            val toolbar: Toolbar = findViewById(R.id.toolbar)
            val layoutToolbar = toolbar.layoutParams
            layoutToolbar.width = height
            toolbar.layoutParams = layoutToolbar
        }

        var layout = main.layoutParams
        layout.width = totalWidth
        main.layoutParams = layout

        val pilesAndDeck : View = findViewById(R.id.pilesAndDeck)
        layout = pilesAndDeck.layoutParams
        layout.height = ((1 + PILE_DECK_TO_FOUNDATIONS_GAP) * cardHeight + 2 * CARD_MARGIN * density).toInt()
        pilesAndDeck.layoutParams = layout
    }

    private fun calculateCardSize() {

       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = this.windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout() or  WindowInsets.Type.systemBars())
            width = metrics.bounds.width() - (insets.left + insets.right)
            height = metrics.bounds.height() - (insets.top + insets.bottom)
            density = resources.displayMetrics.density

        } else {
            val metrics = DisplayMetrics()
            this.windowManager.defaultDisplay.getMetrics(metrics)
            width = metrics.widthPixels
            height = metrics.heightPixels
            density = metrics.density
        }

        Log.d("Window size", "$width $height $density")

        // Remove 14 times (two for each card) the margin, and divide by 7 card rows
        val maxWidth = (width.toFloat() - 14F * CARD_MARGIN * density) / 7F

        //  Maximum display height:
        //      Piles and Deck = (1 + PILE_DECK_TO_FOUNDATIONS_GAP) * cardHeight + 2 * CARD_MARGIN
        //      Foundations = (6 * CARD_HIDDEN_PERCENT + 13 * CARD_SHOW_PERCENT / 2) * cardHeight +  CARD_MARGIN
        val maxFoundationHeight = (6 * CARD_HIDDEN_PERCENT + 13 * CARD_SHOW_PERCENT)
            .coerceAtMost(FOUNDATION_HEIGHT_LIMIT) //Limited for better visibility in most cases
        val maxHeight = ((height.toFloat() - 3 * CARD_MARGIN * density)
                / (1 + PILE_DECK_TO_FOUNDATIONS_GAP + maxFoundationHeight)
            ).toFloat()

        Log.d("Max card size", "$maxWidth $maxHeight")

        if (maxWidth > maxHeight * CARD_ASPECT_RATIO) {
            cardWidth = (maxHeight * CARD_ASPECT_RATIO).toInt()
            cardHeight = maxHeight.toInt()
            landscape = true
        } else {
            cardWidth = maxWidth.toInt()
            cardHeight = (maxWidth / CARD_ASPECT_RATIO).toInt()
            landscape = false // should already be
        }

        cardWidth = maxWidth.coerceAtMost(maxHeight * CARD_ASPECT_RATIO).toInt()
        cardHeight = maxHeight.coerceAtMost(maxWidth / CARD_ASPECT_RATIO).toInt()

        Log.d("Card size", "$cardWidth $cardHeight")

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)

        return true
    }

    override fun onOptionsItemSelected (item: MenuItem) : Boolean {
        return when (item.itemId) {
            R.id.action_newGame -> {
                AlertDialog.Builder(this)
                    .setMessage(getString(R.string.new_game_confirm))
                    .setPositiveButton(getString(R.string.yes)) { _: DialogInterface, _: Int ->
                        klondike = Klondike(AmericanCards())
                        drawStatus()
                    }
                    .setNegativeButton(getString(R.string.no), null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun drawStatus() {
        val status = klondike.status
        clickStatuses.clear()

        drawDeck(status.deck())
        drawPiles(status.piles())
        drawFoundations(status.foundations())
    }

    private fun drawFoundations(foundations: List<Foundation.FoundationStatus>) {
        var i = 0
        drawFoundation(i, foundations[i++], R.id.Foundation1)
        drawFoundation(i, foundations[i++], R.id.Foundation2)
        drawFoundation(i, foundations[i++], R.id.Foundation3)
        drawFoundation(i, foundations[i++], R.id.Foundation4)
        drawFoundation(i, foundations[i++], R.id.Foundation5)
        drawFoundation(i, foundations[i++], R.id.Foundation6)
        drawFoundation(i, foundations[i++], R.id.Foundation7)
    }

    private fun drawFoundation(index: Int, status: Foundation.FoundationStatus, id: Int) {
        var view : ConstraintLayout = findViewById(id)
        val holder = Klondike.CardHolder(Klondike.CardHolderType.FOUNDATION, index)

        view.setOnDragListener (generateDragListenerContainer(holder))

        view.removeAllViews()

        var i = 0
        var imageView: ImageView?
        val numHidden = status.numHidden()
        val numVisible = status.visible().size
        while (i < numHidden) {
            imageView = prepareFoundationCard(view, calculateSeparation(i, numHidden, numVisible))
            view = (imageView.parent as ConstraintLayout?)!!
            drawBackCard(imageView)
            i++
        }

        val lastCardIndex = i + status.visible().size
        status.visible().forEach { card ->
            imageView = prepareFoundationCard(view, calculateSeparation(i, numHidden, numVisible))
            view = (imageView?.parent as ConstraintLayout?)!!
            val cardHolder = CardHolderAndNumber(holder, lastCardIndex - i)
            drawCard(card, imageView!!, view, cardHolder)
            imageView!!.setOnDragListener(generateDragListener(view, cardHolder))
            i++
        }

        if (numHidden == 0 && status.visible().isEmpty()) {
            imageView = prepareFoundationCard(view, 0)
            drawEmptyCard(imageView!!)
            i++
        }

    }

    private fun calculateSeparation(i: Int, numHidden: Int, numVisible: Int): Int {
        if (i == 0) return 0
        if (i <= numHidden) return (cardHeight * CARD_HIDDEN_PERCENT).toInt()
        if (landscape &&
            numHidden * CARD_HIDDEN_PERCENT + numVisible * CARD_SHOW_PERCENT > FOUNDATION_HEIGHT_LIMIT) {

            return ((FOUNDATION_HEIGHT_LIMIT - numHidden * CARD_HIDDEN_PERCENT - CARD_SHOW_PERCENT) *
                    cardHeight / (numVisible - 1)).toInt()
        }
        return (cardHeight * CARD_SHOW_PERCENT).toInt()
    }

    private fun prepareFoundationCard(
        parent: ConstraintLayout,
        separation: Int
    ): ImageView {
        val container = ConstraintLayout(this)
        container.id = View.generateViewId()

        container.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        parent.addView(container)
        applyConstraints(parent, container, separation)

        val imageView = ImageView(this)
        imageView.id = View.generateViewId()

        imageView.scaleType = ImageView.ScaleType.FIT_START
        imageView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        container.addView(imageView)

        applyConstraints(container, imageView, 0)
        return imageView
    }

    private fun applyConstraints(
        parent: ConstraintLayout,
        child: View,
        separation: Int
    ) {
        val constraintLayout = findViewById<ConstraintLayout>(parent.id)
        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)
        constraintSet.connect(
            child.id,
            RIGHT,
            parent.id,
            RIGHT,
            0
        )
        constraintSet.connect(
            child.id,
            TOP,
            parent.id,
            TOP,
            separation
        )
        constraintSet.applyTo(constraintLayout)
    }

    private fun drawPiles(piles: List<Pile.PileStatus>) {
        var i = 0
        drawPile(i, piles[i++], R.id.Pile1, R.id.Pile1Container)
        drawPile(i, piles[i++], R.id.Pile2, R.id.Pile2Container)
        drawPile(i, piles[i++], R.id.Pile3, R.id.Pile3Container)
        drawPile(i, piles[i++], R.id.Pile4, R.id.Pile4Container)
    }

    private fun drawPile(index: Int, status: Pile.PileStatus, pileId: Int, containerId: Int) {
        val cardHolder = Klondike.CardHolder(Klondike.CardHolderType.PILE, index)
        drawCard(status.topCard(), pileId, cardHolder)
        val container = findViewById<ConstraintLayout>(containerId)

        val view = findViewById<ImageView>(pileId)
        view.setOnDragListener(generateDragListener(view, CardHolderAndNumber(cardHolder, 1)))
        container.setOnDragListener(generateDragListenerContainer(cardHolder))
    }

    private fun drawDeck(status: Deck.DeckStatus) {
        val stock: ImageView = findViewById(R.id.DeckStock)

        if (status.cardsOnStock() > 0) {
            stock.setImageResource(R.drawable.card_back)
        } else {
            stock.setImageResource(R.drawable.card_empty)
        }
        setCardImgSize(stock)

        stock.setOnClickListener {
            callKlondike { klondike.take() }
        }

        val waste: ImageView = findViewById(R.id.DeckWaste)
        val cardHolder = Klondike.CardHolder(Klondike.CardHolderType.DECK)
        drawCard(status.topCardOnWaste(), waste, cardHolder)
        waste.setOnDragListener(generateDragListener(waste,
            CardHolderAndNumber(cardHolder, 1)))
    }


    private fun callKlondike (action: () -> Unit) : Boolean{
        var status = false
        try {
            action()
            drawStatus()
            status = true
        } catch (e: IllegalStateException) {
            showError(getString(R.string.messageInvalidMovement))
        } catch (e:IllegalArgumentException) {
            showError(getString(R.string.MessageIllegalMovement))
        } catch (e: RuntimeException) {
            showError(getString(R.string.MessageUnknownError))
        }

        return status
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private data class ClickStatus (val clickTime: Long, var clickCount: Int, var dragCancelled: Boolean)
    private val clickStatuses : HashMap<Int /* view id */, ClickStatus> = HashMap()

    private fun drawCard(
        card: Optional<Card>,
        imageView: ImageView,
        cardHolder: Klondike.CardHolder
    ) {
        drawCard(card, imageView, CardHolderAndNumber(cardHolder, 1))
    }

    private fun drawCard(
        card: Optional<Card>,
        imageView: ImageView,
        cardHolder: CardHolderAndNumber
    ) {
        if (card.isPresent) {
            drawCard(card.get(), imageView, cardHolder)
        } else {
            drawEmptyCard(imageView)
        }
    }
    private fun drawCard(
        card: Card,
        imageView: ImageView,
        cardHolder: CardHolderAndNumber
    ) {
        drawCard(card, imageView, imageView, cardHolder)
    }

    private fun drawCard(
        card: Optional<Card>,
        id: Int,
        cardHolder: Klondike.CardHolder
    ) {
        drawCard(card, findViewById(id), cardHolder)
    }

    private fun drawCard(
        card: Card,
        imageView: ImageView,
        draggableView: View,
        cardHolder: CardHolderAndNumber
    ) {
        imageView.setImageResource(getCardImage(card))
        setCardImgSize(imageView)
        draggableView.setOnTouchListener(onLongClickCard(draggableView, cardHolder))
        imageView.visibility = View.VISIBLE
    }

    private fun setCardImgSize(imageView: ImageView) {
        val layoutParams = imageView.layoutParams
        Log.d("Layout Params", "${layoutParams.javaClass} -> $layoutParams ${layoutParams.width} ${layoutParams.height}")
        if (layoutParams is ConstraintLayout.LayoutParams) {
            layoutParams.width = cardWidth
            layoutParams.height = cardHeight

            imageView.layoutParams = layoutParams
        }
    }

    private fun drawEmptyCard(imageView: ImageView) {
        imageView.setImageResource(R.drawable.card_empty)
        setCardImgSize(imageView)
        imageView.visibility = View.VISIBLE
    }

    private fun drawBackCard(imageView: ImageView) {
        imageView.setImageResource(R.drawable.card_back)
        setCardImgSize(imageView)
        imageView.visibility = View.VISIBLE
    }

     private fun onLongClickCard(view: View, cardHolder: CardHolderAndNumber) : (v: View, motionEvent: MotionEvent)
            -> Boolean {
        return fun  (v: View, motionEvent: MotionEvent) : Boolean {
            val clickStatus = clickStatuses[view.id]
            val currentTimeMillis = System.currentTimeMillis()

            return when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (clickStatus == null ||
                        currentTimeMillis - clickStatus.clickTime > DOUBLE_CLICK_TIMEOUT) {
                        clickStatuses[view.id] = ClickStatus(currentTimeMillis, 1, false)
                    } else {
                        clickStatus.clickCount++
                        clickStatus.dragCancelled = true
                    }
                    Timer().schedule(timerTask {
                        val status = clickStatuses[view.id]
                        if (status != null && !status.dragCancelled) {
                            Log.w("startDragAndDrop", cardHolder.toString())
                            val cardHolderStr = jacksonObjectMapper().writeValueAsString(cardHolder)
                            v.startDragAndDrop(
                                ClipData.newPlainText(cardHolderStr, cardHolderStr),
                                View.DragShadowBuilder(view),
                                cardHolder,
                                0
                            )
                        }
                    }, DRAG_TIMEOUT)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    clickStatus?.dragCancelled = true
                    if (clickStatus != null && clickStatus.clickCount > 1) {
                        callKlondike { klondike.toPile(cardHolder.cardHolder) }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    clickStatus?.dragCancelled = true
                    false
                }
                else -> false
            }
        }
    }

    private fun generateDragListenerContainer(to: Klondike.CardHolder) :
                (v: View, e: DragEvent) -> Boolean {
        return lambda@{ v, e ->
            Log.d("DRAG_CONTAINER", "${e.action} $to $e")
            val from = getFrom(e) ?: return@lambda false
            val isMe = from.cardHolder == to
            when (e.action) {
                DragEvent.ACTION_DRAG_STARTED -> !isMe
                DragEvent.ACTION_DRAG_ENTERED -> {
                    if (!isMe) {
                        val color = if (klondike.canMoveCards(from.cardHolder, to, from.number)) {
                            Color.argb(128, 128, 255, 128)
                        } else {
                            Color.argb(128, 255, 128, 128)
                        }
                        applyToAllImages(v) { i -> i.setColorFilter(color) }
                        v.invalidate()
                    }
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    if (!isMe) {
                        applyToAllImages(v) { i -> i.clearColorFilter() }
                        v.invalidate()
                    }
                    true
                }
                DragEvent.ACTION_DROP -> {
                    if (isMe) {
                        false
                    } else {
                        applyToAllImages(v) {i -> i.clearColorFilter() }
                        v.invalidate()
                        callKlondike {
                            klondike.moveCards(from.cardHolder, to, from.number)
                        }
                    }
                }
                else -> false
            }
        }
    }

    private fun applyToAllImages(v: View, consumer: (i: ImageView) -> Unit) {
        when (v) {
            is ImageView -> consumer.invoke(v)
            is ViewGroup -> v.children.forEach { x -> applyToAllImages(x, consumer) }
            else -> {}
        }
    }

    private fun generateDragListener(view: View, to: CardHolderAndNumber) :
                (v: View, e: DragEvent) -> Boolean {
        return lambda@{ _, e ->
            Log.d("DRAG", "${e.action} $to $e")
            val from = getFrom(e) ?: return@lambda false
            val isMe = from == to
            when (e.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    if (isMe) {
                        view.visibility = View.INVISIBLE
                    }
                    isMe
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    Log.d("DRAG_ENDED", "$isMe ${e.result}")
                    if (isMe && !e.result) {
                        Log.d("DRAG_ENDED", "setting visible")
                        view.visibility = View.VISIBLE
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun getFrom(e: DragEvent) : CardHolderAndNumber? {
        var from = e.localState as? CardHolderAndNumber?
        if (from == null) {
            try {
                from = jacksonObjectMapper().readValue(e.clipDescription.label.toString())
            } catch (e: RuntimeException) {
                return null
            }
        }
        return from
    }

    private fun getCardImage(card: Card): Int {
        val rank = when (card.rank().name) {
            "J" -> "jack"
            "Q" -> "queen"
            "K" -> "king"
            else -> card.rank().name
        }

        val suit = card.suit().name
            .substring(0, card.suit().name.length - 1)
            .lowercase()

        val cardName = "card_${rank}_${suit}"
        val cardId : KProperty<Int> = R.drawable::class.members.first { callable ->
            callable is KProperty && cardName == callable.name
        } as KProperty<Int>

        return cardId.getter.call()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KLONDIKE_STATE_NAME,jacksonObjectMapper().writeValueAsString(klondike))
        super.onSaveInstanceState(outState)
    }
}