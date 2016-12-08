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

# Concrete State Passing

We've seen that it's useful to have pure functions that take state (and other
arguments) and return a result and the new state. In Haskell, it this sort
of function could be written (in the case of no arguments) as being of type
`s -> (r, s)` where `s` is the type of our state, and `r` is the type of
our result. In Clojure, a function meeting this type which returns 0 and does
not alter its state could be written as `(fn [s] [0 s])`. If it takes an
extra parameter, it could be something like `(fn [x s] [(< x 2) s])`, which
is indeed the signature of the `attack-monster` function above.

To use a concrete example, let's write a simple, pure PRNG in classic
Clojure.

```clojure
(defn xorshift64*
  "Implements the raw xorshift64* random algorithm. Takes the seed/state and returns the
   next 'random' number and the new seed. Since Clojure doesn't have unsigned 64-bit
   integers, but does have 64-bit signed integers, the seed and the result may be
   negative. These numbers can be made positive (and equivalent to the C algorithm's
   output) by converting to BigInt and adding 2^64. Input seed/state should may not be zero.
   See: https://en.wikipedia.org/wiki/Xorshift"
  [x]
  (let [a (bit-xor x (unsigned-bit-shift-right x 12))
        b (bit-xor a (bit-shift-left a 25))
        c (bit-xor b (unsigned-bit-shift-right b 27))
        ;; unchecked prevents math overflow
        r (unchecked-multiply c 2685821657736338717)]
    [r c]))
```

This results in output like this:

```
#'user/xorshift64*
user=> (xorshift64* 3)
[-2905267188090366121 100663299]
user=> (xorshift64* (second (xorshift64* 3)))
[247403287327551319 3378524379445251]
user=> 
```

We can convert this to a random number from 0 to a specified bound as follows:

```clojure
(defn rand-int
  "Takes an (exclusive) bound and a random state/seed and returns a random number from
   0 to that bound as well as the new random state/seed."
  [n rand-state]
  (let [[r new-state] (xorshift64* rand-state)]
    [(mod r n) new-state]))
```

Again, this can be used simply:

```
#'user/rand-int
user=> (rand-int 10 1)
[5 33554433]
user=> (rand-int 10 3)
[9 100663299]
user=> (rand-int 10 (second (rand-int 10 3)))
[9 3378524379445251]
user=> 
```

We can see that the random seeds change the same way, but the random number output is
modified as per the `mod`.

Now, if you wanted 10 random numbers, or N random numbers, things would get a bit
more complicated as the system would have to track the random numbers and the seeds
through each stage.

```clojure
(defn three-rand
  "Gets three random numbers between 0 and 9 inclusive. Takes a seed, and returns
   the random numbers as a vector and the seed (both encapsulated as a vector)."
  [s]
  (let [[r1 s] (rand-int 10 s)
        [r2 s] (rand-int 10 s)
        [r3 s] (rand-int 10 s)]
    [[r1 r2 r3] s]))
```

And the output:

```
#'user/three-rand
user=> (three-rand 3)
[[9 9 4] 6474749221828612]
```

Now, imagine you had tons of state-modifying things going on, such as a game
function that attacks a monster. It would get a random number to check if the
attack hits. Then, a random number for damage. Then the state of the game would
be updated, and a message added to the log. Then, of course, the AI would get to
act and the monster could attack back, and more random numbers would need to be
generated, and the state would keep being modified. Each part would need to
keep the state updated and propagated properly, manage return values correctly
separately from the state, and whatnot. Wouldn't it be easier if we could write
code that looked like:

```clojure
  (let-with-state
    [r1 (rand-int 10)
     r2 (rand-int 10)
     r3 (rand-int 10)]
    [r1 r2 r3])
```

...and have the state management happen automatically and be propagated from one call
to the next and returned from the function?

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

We now have a function, `(rand-int <n> <rand-state>)`. Through a technique called
[currying](https://en.wikipedia.org/wiki/Currying), we can turn this one function of
two arguments into a function of one argument that returns a function that takes
a second argument, which together accomplishes the same exact thing. Let's
try this by making a function that takes `<n>` as the argument and returns a function
that takes `<rand-state>` as an argument, which itself then returns the random number
up to `<n>` and the new `<rand-state>`.

```clojure
(defn rand-int'
  "Takes an (exclusive) bound and returns a function that takes
   a random state/seed which returns a random number from
   0 to that bound as well as the new random state/seed."
  [n]
  (fn [rand-state]
    (let [[r new-state] (xorshift64* rand-state)]
      [(mod r n) new-state])))
```

Since Clojure is a [Lisp-1](https://en.wikipedia.org/wiki/Common_Lisp#The_function_namespace), we
can easily call this new function similarly to the one above:

```
#'user/rand-int'
user=> (rand-int' 10)
;; NOTE: This output was generated using ClojureJVM 1.7.0 instead of ClojureCLR
#object[user$rand_int_SINGLEQUOTE_$fn__32 0x7905a0b8 "user$rand_int_SINGLEQUOTE_$fn__32@7905a0b8"]
user=> ((rand-int' 10) 3)
[9 100663299]
user=> ((rand-int' 10) (second ((rand-int' 10) 3)))
[9 3378524379445251]
user=> (def rand10 (rand-int' 10))
#'user/rand10
user=> (rand10 3)
[9 100663299]
user=> (rand10 (second (rand10 3)))
[9 3378524379445251]
user=> 
```

So, the first thing necessary to use the "state monad" is to mentally refactor all our
state-using functions into curried functions of the form of `rand-int'` above, which
in general looks like this:

```clojure
(defn state-monad-function
  [whatever non-state arguments]
  (fn [initial-state]
    ...
    [retval final-state]))
```

That is, refactor our argument and state-using and returning functions
into functions of non-state arguments which return functions of the state, which in turn returns the
function's value as well as the final state.

## Preparatory Interlude

For the next part, clone these two GitHub repos:

* [clojure.algo.monads](https://github.com/clojure/algo.monads)
* [clojure.tools.macro](https://github.com/clojure/tools.macro)

Then, copy the subdirectories of `src/main/clojure` (which will include a new top-level `clojure` directory)
to where your ClojureJVM JAR is, and invoke your REPL with something like 
`rlwrap -r java -cp .:clojure-1.7.0.jar clojure.main`. Then, run
`(use 'clojure.algo.monads)` at the REPL `user=>` prompt.

Please note that I'm using ClojureJVM because it can use the libraries above
directly without modfication. If you wish to use ClojureCLR, you can do the
same thing if you use my ClojureCLR ported versions in the Aiband GitHub repository
along with [Nostrand](https://github.com/nasser/nostrand) to get a ClojureCLR REPL.
Also, [rlwrap](https://github.com/hanslub42/rlwrap) makes REPLs better. 

## Continuing after Preparatory Interlude

As indicated earlier, the `rand-int'` function is now usable as a "state monadic function,"
(which is not by any means a canonical name, just my informal moniker for the purposes of
this discussion)
which state is the random seed. We also "wished" for a `let-with-state` that automatically
propagated and handled propagating the state between multiple calls. Well... Our wish has
been answered. We can now rewrite `three-rand` using these tools:

```clojure
(defn three-rand'
  "'State monadic function' of no arguments which returns three random numbers (and, of course,
   the final state)."
  []
  (domonad state-m
    [r1 (rand-int' 10)
     r2 (rand-int' 10)
     r3 (rand-int' 10)]
    [r1 r2 r3]))
```

In fact, examining the above code, we see the only difference between what was wished
for and what was actually typed is that `let-with-state` became the mystical incantation
`domonad state-m`. Nowhere do we see any obvious state variables. Let's see how this works
in the (ClojureJVM, but ClojureCLR should work the same) REPL:

```
#'user/three-rand'
user=> (three-rand')
#object[clojure.algo.monads$fn__272$m_bind_state__277$fn__278 0x52045dbe "clojure.algo.monads$fn__272$m_bind_state__277$fn__278@52045dbe"]
user=> ((three-rand') 3)
[[9 9 4] 6474749221828612]
user=> 
```

As we can see, `three-rand'` is a function of no arguments that returns a function,
just as our `rand-int'` function did. Calling that function with the initial state
produces the same output as the original `three-rand` function, including the
new final state - but we never coded state management into `three-rand'` explictly!

Moreover, since `three-rand'` is of the same form as `rand-int'` they can be composed
using the same `domonad state-m` form:

```clojure
(defn ten-rand'
  "'State monadic function' of no arguments which returns ten random numbers (and, of course,
   the final state)."
  []
  (domonad state-m
    [t1 (three-rand')
     r1 (rand-int' 10)
     t2 (three-rand')
     t3 (three-rand')]
    (into [] (concat t1 [r1] t2 t3))))
```

Which results in the expected output:

```
#'user/ten-rand'
user=> ((ten-rand') 3)
[[9 9 4 2 1 2 3 5 7 3] 2993683514635247533]
user=> 
```

# First Explanation of `domonad state-m`

At initial glance, `domonad state-m` really does seem to be a version of
`let` that also threads and returns state. The first argument, a vector,
takes pairs of the form `<binding> <function-of-state>`. As we have
seen, the `<function-of-state>` is simply a function that takes a
single parameter, the current state, and returns a vector of two items,
the return value and the new state.

In the example above, the `<function-of-state>` was obtained by
evaluating the form `(rand-int' 10)`, which returns a function of the
expected form. It's not necessary for this form to be a function call;
direct use of an appropriate function also works:

```
user=> ((domonad state-m [x #(vector % %)] x) 3)
[3 3]
user=> (def state-inc-func #(vector (inc %) %))
#'user/state-inc-func
user=> ((domonad state-m [x state-inc-func] x) 3)
[4 3]
```

Indeed, for "state monadic functions" of no arguments, they can be
written without that intermediate step and used directly, just as in
the second example above:

```clojure
(def ten-rand''
  "A bare state-only function which returns ten random numbers (and, of course,
   the final state)."
  (domonad state-m
    [t1 (three-rand')
     r1 (rand-int' 10)
     t2 (three-rand')
     t3 (three-rand')]
    (into [] (concat t1 [r1] t2 t3))))
```

Results in the expected output:

```
#'user/ten-rand''
user=> ((domonad state-m [x ten-rand''] x) 3)
[[9 9 4 2 1 2 3 5 7 3] 2993683514635247533]
user=>
```

So, the `domonad state-m` vector consists of the bindings
and the state functions to call to get the return value and
updated state. There can be an unlimited number of these.
Then, the final form after the vector is evaluated and that
is used as the final return value of the `domonad state-m`,
along with the final state from the evaluation of the vector.
All the bindings made in the vector are available to use in
the final form, as was seen with `t1 [r1] t2 t3`. The final
form to be evaluated, however, is a regular Clojure form rather
than the special "function of state returning vector of return
value and new state" used in the vector of the `domonad state-m`.

The `domonad` actually allows for additional entries in the
vector beyond `<binding> <function-of-state>` which are a
`:let`, `:cond` and `:if`/`:then`/`:else` (and also a
`:when` which is not useful for the
state monad).

## `:let`

`:let` simply allows for bindings to be made with ordinary
Clojure forms. For example:

```clojure
(def test-let
  "Demonstrate the :let clause in domonad with random numbers."
  (domonad state-m
    [r1 (rand-int' 10)
     r2 (rand-int' 10)
     :let [rsum  (+ r1 r2)
           rdiff (- r1 r2)]
     r3 (rand-int' (inc rsum))]
    [r1 r2 rsum rdiff r3]))
```

Which has (what I hope is) the expected output:

```
#'user/test-let
user=> (test-let 3)
[[9 9 18 0 17] 6474749221828612]
user=> (test-let 4)
[[1 9 10 -8 9] 13512173405898766]
user=> ((domonad state-m [x test-let] x) 3)
[[9 9 18 0 17] 6474749221828612]
user=> 
```

I personally found the `:let` construct to be a little inelegant, so I wrote
a very simple "state monadic function" that simply returns its argument 
and unchanged state, or a function of its remaining arguments and again
unchanged state:

```clojure
(defn <-
  "Returns a state-monad function that returns the state unchanged
   and the value passed in as the return value. Equivalent to a 'let'.
   If multiple values are passed in, it is assumed that we should apply
   the second and subsequent values to the first which is a function."
  [v & rest]
    (if (nil? rest)
      (fn [s] [v s])
      (fn [s] [(apply v rest) s])))

(def test-<-
  "Demonstrate the <- in lieu of a :let clause in domonad with random numbers."
  (domonad state-m
    [r1 (rand-int' 10)
     r2 (rand-int' 10)
     rsum  (<- (+ r1 r2))
     rsum' (<- + r1 r2)
     rdiff (<- - r1 r2)
     r3 (rand-int' (inc rsum))]
    [r1 r2 rsum rsum' rdiff r3]))
```

```
user=> (test-<- 3)
[[9 9 18 18 0 17] 6474749221828612]
user=> (test-<- 4)
[[1 9 10 10 -8 9] 13512173405898766]
```

One last detail: There is already a pre-defined function that works this way.
It's a core part of the monad definition and is called `m-result`. This works
exactly as the single-argument version of `<-`.

```clojure
(def test-m-result
  "Demonstrate the m-result in lieu of a :let clause in domonad with random numbers."
  (domonad state-m
    [r1 (rand-int' 10)
     r2 (rand-int' 10)
     rsum  (m-result (+ r1 r2))
     rdiff (m-result (- r1 r2))
     r3 (rand-int' (inc rsum))]
    [r1 r2 rsum rdiff r3]))
```

```
#'user/test-m-result
user=> (test-m-result 3)
[[9 9 18 0 17] 6474749221828612]
user=> 
```

## `:if`

TODO

## `:cond`

TODO

# Complex State

TODO: Using state containing more state and multiple types of "state monadic" functions of different state together. Hoisting.

# Other Topics

TODO: Lifting. ...?

## Note on `domonad`

I personally find the syntax of `clojure.algo.monads`'s `domonad` clause to be
pretty ugly. I prefer the syntax of [BWO's `mdo`](https://github.com/bwo/monads),
but porting that library to ClojureCLR was beyond my intention for the time being.
