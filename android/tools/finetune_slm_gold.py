"""Expected labels for corpus entries; shared by finetune_slm.py and check_tflite.py."""
import re
import make_corpus as mc

LABELS = {"piece": dict(H="hours", P="pieces", R="rate", A="advance"), "wage_recv": dict(W="received"), "wage_unpaid": dict(W="wage"),
          "exp_food": dict(E="expense"), "exp_travel": dict(E="expense"), "exp_health": dict(E="expense"), "adv": dict(A="advance"),
          "yday": dict(H="hours", W="wage"), "hourly": dict(H="hours", HR="rate")}

def gold(c):
    e = c["entries"]; k = c["template"]
    vals = {"piece": lambda: [e[0]["hours"], e[0]["pieces"], e[0]["rate"], e[1]["amount"]], "wage_recv": lambda: [e[0]["amount"]], "wage_unpaid": lambda: [e[0]["amount"]],
            "adv": lambda: [e[0]["amount"]], "yday": lambda: [e[0]["hours"], e[0]["amount"]], "hourly": lambda: [e[0]["hours"], e[0]["rate"]]}.get(k, lambda: [e[0]["amount"]])()
    order = re.findall(r"\{(\w+)\}", mc.T[c["lang"]][k])
    return vals, [LABELS[k][x] for x in order]

