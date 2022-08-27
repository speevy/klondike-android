package net.speevy.klondike

import android.app.AlertDialog
import android.content.ClipData
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
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

class MainActivity : AppCompatActivity() {
    private var klondike = Klondike(AmericanCards())
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val state = savedInstanceState?.getString(KLONDIKE_STATE_NAME)
        if (state != null) {
            klondike = jacksonObjectMapper().readValue(state)
        }
        setContentView(R.layout.activity_main)

        drawStatus()

        val callback: OnBackPressedCallback = object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                klondike.undo()
                drawStatus()
            }
        }

        onBackPressedDispatcher.addCallback(this, callback)

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
        var imageView: ImageView? = null
        val numHidden = status.numHidden()
        while (i < numHidden) {
            imageView = prepareFoundationCard(view, calculateSeparation(i, numHidden))
            view = (imageView.parent as ConstraintLayout?)!!
            drawBackCard(imageView)
            i++
        }

        val lastCardIndex = i + status.visible().size
        status.visible().forEach { card ->
            imageView = prepareFoundationCard(view, calculateSeparation(i, numHidden))
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

    private fun calculateSeparation(i: Int, numHidden: Int): Int {
        if (i == 0) return 0
        if (i <= numHidden) return 16
        return 40
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
            ConstraintSet.RIGHT,
            parent.id,
            ConstraintSet.RIGHT,
            0
        )
        constraintSet.connect(
            child.id,
            ConstraintSet.TOP,
            parent.id,
            ConstraintSet.TOP,
            separation
        )
        constraintSet.applyTo(constraintLayout)
    }

    private fun drawPiles(piles: List<Pile.PileStatus>) {
        var i = 0
        drawPile(i, piles[i++], R.id.Pile1)
        drawPile(i, piles[i++], R.id.Pile2)
        drawPile(i, piles[i++], R.id.Pile3)
        drawPile(i, piles[i++], R.id.Pile4)
    }

    private fun drawPile(index: Int, status: Pile.PileStatus, pileId: Int) {
        val cardHolder = Klondike.CardHolder(Klondike.CardHolderType.PILE, index)
        drawCard(status.topCard(), pileId, cardHolder)
        val view = findViewById<ImageView>(pileId)
        view.setOnDragListener {v: View, e: DragEvent ->
            generateDragListener(view, CardHolderAndNumber(cardHolder, 1)).invoke(v, e) ||
            generateDragListenerContainer(cardHolder).invoke(v, e)
        }
    }

    private fun drawDeck(status: Deck.DeckStatus) {
        val stock: ImageView = findViewById(R.id.DeckStock)

        if (status.cardsOnStock() > 0) {
            stock.setImageResource(R.drawable.card_back)
        } else {
            stock.setImageResource(R.drawable.card_empty)
        }

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
        draggableView.setOnTouchListener(onLongClickCard(draggableView, cardHolder))
        imageView.visibility = View.VISIBLE
    }

    private fun drawEmptyCard(imageView: ImageView) {
        imageView.setImageResource(R.drawable.card_empty)
        imageView.visibility = View.VISIBLE
    }

    private fun drawBackCard(imageView: ImageView) {
        imageView.setImageResource(R.drawable.card_back)
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
        return lambda@{ v, e ->
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