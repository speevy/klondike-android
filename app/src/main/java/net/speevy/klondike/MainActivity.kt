package net.speevy.klondike

import android.content.ClipData
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import speevy.cardGames.AmericanCards
import speevy.cardGames.Card
import speevy.cardGames.klondike.Deck
import speevy.cardGames.klondike.Foundation
import speevy.cardGames.klondike.Klondike
import speevy.cardGames.klondike.Pile
import java.lang.IllegalArgumentException
import java.lang.RuntimeException
import java.util.*
import kotlin.collections.HashMap
import kotlin.reflect.KProperty


class MainActivity : AppCompatActivity() {
    private var klondike = Klondike(AmericanCards())
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawStatus()
    }

    private fun drawStatus() {
        val status = klondike.status
        doubleClickTiming.clear()
        cardHolders.clear()

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
        val view : ConstraintLayout = findViewById(id)
        val holder = Klondike.CardHolder(Klondike.CardHolderType.FOUNDATION, index)

        view.removeAllViews()

        var i = 0
        var imageView: ImageView? = null
        while (i < status.numHidden()) {
            imageView = prepareFoundationCard(view, id, i++)
            drawBackCard(imageView, holder)
        }

        val lastCardIndex = i + status.visible().size
        status.visible().forEach { card ->
            imageView = prepareFoundationCard(view, id, i++)
            drawCard(card, imageView!!, CardHolderAndNumber(holder, lastCardIndex + 1 - i))
        }

        if (status.numHidden() == 0 && status.visible().isEmpty()) {
            imageView = prepareFoundationCard(view, id, i++)
            drawEmptyCard(imageView!!, CardHolderAndNumber(holder, 1))
        }

        setOnDragListener(imageView!!)
    }

    private fun prepareFoundationCard(
        view: ConstraintLayout,
        id: Int,
        i: Int
    ): ImageView {
        val imageView = ImageView(this)
        imageView.id = View.generateViewId()

        imageView.scaleType = ImageView.ScaleType.FIT_START
        imageView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        view.addView(imageView)


        val constraintLayout = findViewById<ConstraintLayout>(id)
        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)
        constraintSet.connect(
            imageView.id,
            ConstraintSet.RIGHT,
            id,
            ConstraintSet.RIGHT,
            0
        )
        constraintSet.connect(
            imageView.id,
            ConstraintSet.TOP,
            id,
            ConstraintSet.TOP,
            i * 16
        )
        constraintSet.applyTo(constraintLayout)
        return imageView
    }

    private fun drawPiles(piles: List<Pile.PileStatus>) {
        var i = 0
        drawPile(i, piles[i++], R.id.Pile1)
        drawPile(i, piles[i++], R.id.Pile2)
        drawPile(i, piles[i++], R.id.Pile3)
        drawPile(i, piles[i++], R.id.Pile4)
    }

    private fun drawPile(index: Int, status: Pile.PileStatus, pileId: Int) {
        drawCard(status.topCard(), pileId, Klondike.CardHolder(Klondike.CardHolderType.PILE, index))
        setOnDragListener(findViewById(pileId))
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
        drawCard(status.topCardOnWaste(), waste, Klondike.CardHolder(Klondike.CardHolderType.DECK))
    }

    private fun drawCard(
        card: Card,
        imageView: ImageView,
        cardHolder: CardHolderAndNumber
    ) {
        imageView.setImageResource(getCardImage(card))
        associateCardHolder(imageView, cardHolder)
        //imageView.setOnClickListener(this::onClickCard)
        imageView.setOnTouchListener(onLongClickCard(imageView))
    }

    private fun drawCard(
        card: Optional<Card>,
        id: Int,
        cardHolder: Klondike.CardHolder
    ) {
        drawCard(card, findViewById(id), cardHolder)
    }

    private fun callKlondike (action: () -> Unit) {
        try {
            action()
            drawStatus()
        } catch (e: IllegalStateException) {
            showError("Invalid Movement")
        } catch (e:IllegalArgumentException) {
            showError("Illegal Movement")
        } catch (e: RuntimeException) {
            showError("Unknown error")
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private val doubleClickTiming : HashMap<Int /* view id */, Long /* last click time */> = HashMap()
    private val doubleClickCount : HashMap<Int /* view id */, Int> = HashMap()
    private val cardHolders: HashMap<Int /* view id */, CardHolderAndNumber> = HashMap()

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
            drawEmptyCard(imageView, cardHolder)
        }
    }

    private fun drawEmptyCard(
        imageView: ImageView,
        cardHolder: CardHolderAndNumber
    ) {
        imageView.setImageResource(R.drawable.card_empty)
        associateCardHolder(imageView, cardHolder)
    }

    private fun drawBackCard(
        imageView: ImageView,
        cardHolder: Klondike.CardHolder
    ) {
        imageView.setImageResource(R.drawable.card_back)
        associateCardHolder(imageView, CardHolderAndNumber(cardHolder, 1))
    }

    private fun associateCardHolder (
        imageView: ImageView,
        cardHolder: CardHolderAndNumber
    ) {
        cardHolders[imageView.id] = cardHolder
    }

    private fun onLongClickCard(imageView: ImageView) : (v: View,  motionEvent: MotionEvent)
            -> Boolean {
        return fun  (v: View, motionEvent: MotionEvent) : Boolean {
            val last = doubleClickTiming[imageView.id]
            val currentTimeMillis = System.currentTimeMillis()
            Log.w("CLICK", motionEvent.action.toString())
            return when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (last == null || currentTimeMillis - last > 1000L) {
                        doubleClickTiming[imageView.id] = currentTimeMillis
                        doubleClickCount[imageView.id] = 1
                    } else {
                        doubleClickCount[imageView.id] = doubleClickCount[imageView.id]!! + 1
                    }
                    v.startDragAndDrop(
                        ClipData.newPlainText("", imageView.id.toString()),
                        View.DragShadowBuilder(imageView),
                        cardHolders[imageView.id],
                        0
                    )
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val cardHolder = cardHolders[v.id]
                    if (doubleClickCount[imageView.id]!! > 1) {
                        callKlondike { klondike.toPile(cardHolder?.cardHolder) }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setOnDragListener(imageView: ImageView) {
        imageView.setOnDragListener { v, e ->
            when (e.action) {
                DragEvent.ACTION_DRAG_STARTED -> e.localState != null
                        && e.localState is CardHolderAndNumber
                        && (e.localState as CardHolderAndNumber).cardHolder != cardHolders[imageView.id]?.cardHolder
                DragEvent.ACTION_DRAG_ENTERED -> {
                    (v as? ImageView)?.setColorFilter(Color.GREEN)
                    v.invalidate()
                    true
                }
                DragEvent.ACTION_DRAG_LOCATION -> true
                DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                    (v as? ImageView)?.clearColorFilter()
                    v.invalidate()
                    true
                }
                DragEvent.ACTION_DROP -> {
                    (v as? ImageView)?.clearColorFilter()
                    v.invalidate()
                    val to = cardHolders[imageView.id] ?: return@setOnDragListener false
                    val from = e.localState as CardHolderAndNumber
                    callKlondike {
                        klondike.moveCards(
                            from.cardHolder,
                            to.cardHolder,
                            from.number
                        )
                    }

                    true
                }
                else -> false
            }
        }
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
}