# State Monad in Aiband

Copyright 2016 Douglas P. Fields, Jr.  
symbolics _at_ lisp.engineer  
https://symbolics.lisp.engineer/  
https://twitter.com/LispEngineer  
https://github.com/LispEngineer  


Two of the goals in building Aiband is to implement a game that has
a full implementation in relatively portable Clojure while maintaining
a clear separation from the UI code (preferably also in Clojure), and
second to build the game engine in an entirely pure fashion following
good functional programming practices.

By entirely pure, I mean that the game code is comprised almost entirely
of pure functions whose outputs are entirely determined by their inputs
without any reference to external state (or having side effects). There
are obviously some exceptions (loading/saving the game, updating the
UI and getting input from the user, and maybe some out-of-band logging
used for debugging) but those can be highly localized
and fed into otherwise pure code.

Although Clojure allows for pure code by providing a solid basic library
of immutable data structures, it also has extensive support for mutable
state through the use of atoms, refs and other constructs. The goal here
is to maintain a single item of mutable state - the "game state," which
is mutated only with user input (this is, after all, a classical
turn-based roguelike game) and only with the output of a pure function
which takes the old state and user command as input.

Obviously, this game state is a complex nesting of various state,
including (but not limited to):

* The player's information
* The current level and all other levels's state, including
  the contents of the level, etc.
* Message history and event logs
* The random number generator state

This state is stored using Clojure's core data structures, primarily
maps, sets and vectors.

# Passing the State Around

One of the challenges of a game built in a pure fashion is that the
state needs to be an input to many functions, and the new state must
be an output from many functions as well. One way to accomplish this
is using Clojure's threading macros, `->` and `->>` (and many others
that have been added via libraries). The problem with these is that
although they do propagate the state input and output from one function
to the next, they don't allow for functions to return values other than
the altered state nor to compose those previous outputs for use in later
stages of the thread.

One straightforward example of where this "thread state plus return values"
is very useful is when operating
a pseudorandom number generator. To be of use, the RNG needs to
output the next "random" number, plus a modified state. 
So, if we imagine the "game state" to be composed of only
(or at least partly) the RNG state, useful functions for the RNG would likely
include something like `(next-int-between <from> <to> <state>)` which
then returns something like `[<random-number> <new-state>]`
(or `(<random-number> <new-state>)` or `{:result <random-number> :state <new-state>}`
or whatever method desired to compose the two distinct outputs).

This sort of call could not be used with Clojure's basic threading macros,
because the input and output are of different types. If we also had a function
`(harm-monster <monster-id> <hps> <state>)` returning the number of remaining
HPs of the target, and which was called from an end-user
function `(attack-monster <monster-id> <state>)`, things could look
somewhat convoluted:

```clojure
(defn attack-monster
  "Outline of a function that allows a player to attack a specific monster,
   always hitting, and returns the new game state."
  [monster-id state]
  (let [[damage  state']   (next-int-between 1 10 state)
        [mon-hps state'']  (harm-monster monster-id state')
        [_       state'''] (add-message state''
        	(if (<= mon-hps 0) "You attack the monster and kill it!"
        	                   (str "You attack the monster for " damage " hits.")))]
    ([(<= mon-hps 0) state'''])))
```

(Obviously, in Clojure, you can call all the `state'''` bindings by the same names,
which would probably be what would be done in practice.)

As can be seen, the need to maintain the return value and state separately can lead
to somewhat convoluted logic.

# Enter the State Monad

(This discussion references the State Monad as used in 
[clojure.algo.monads](https://github.com/clojure/algo.monads). There is a seemingly
better implementation of monads in [bwo.monads](https://github.com/bwo/monads),
but due to its heavy set of dependencies, I chose to use the former library
as it was very easy to port to ClojureCLR, which is what Aiband uses.)

Oh no, "monad." This term scares many software engineers who just want to get things
done without learning about monad laws, category theory, etc. So, I'm not going
to talk to them, but rather to talk about how to use it in Clojure to make the
above sort of code more easily read.

We've seen that it's useful to have pure functions that take state (and other
arguments) and return a result and the new state. In Haskell, it this sort
of function could be written (in the case of no arguments) as being of type
`s -> (r, s)` where `s` is the type of our state, and `r` is the type of
our result. In Clojure, a function meeting this type which returns 0 and does
not alter its state could be written as `(fn [s] [0 s])`. If it takes an
extra parameter, it could be something like `(fn [x s] [(< x 2) s])`, which
is indeed the signature of the `attack-monster` function above.

To use a concrete example, let's write a simple (bad) PRNG in classic
Clojure.

```clojure
(defn next-int-between
  "Returns a 
```














