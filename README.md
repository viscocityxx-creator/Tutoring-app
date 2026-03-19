# AME App (Guided Academic Support)

AME is a Spring Boot web app focused on guided solutions, feedback, editing, and explanations.

## What is implemented
- Sign up / login (session-based)
- Upload assignment bundle (max 5MB total)
- Country restriction to selected African countries
- AI-like category detection + mismatch handling per assignment
- Measurable readability check (score threshold 0.80)
- AI difficulty index `[I]` on 1-3 (rounded to hundredth)
- Re-evaluated difficulty `[i]` on 1-12
- Time prediction flow constrained to 2 hours - 7 days
- Urgency constant `U` with bounds check `[0.4, 30.5]`
- Local currency display + USD internal pricing using live FX API
- Payment step page (Stripe links provided; demo payment action included)
- History page + recent downloads + discourse reviews
- Unlimited-assignment progress tracker

## Core formulas in app
- `Time needed = 2 + ((i / 12) ^ 166) * 166` then rounded to nearest 6 hours (2-hour exception)
- `Total Cost = Base_Price * CEC * AL * I * U`
- `Base_Price = 200`
- `College Application multiplier = 2`
- `In-depth explanation multiplier = 1.5`
- `U = 0.5 * predicted_hours / user_required_hours` when user objects to default

## Run
```bash
gradle bootRun
```

Open:
- `http://localhost:8080`

## Stripe setup docs
- [Accept a payment on web](https://stripe.com/docs/payments/accept-a-payment?platform=web)
- [Stripe dashboard signup](https://dashboard.stripe.com/register)

## Notes
- Legacy tutoring routes are still available under `/legacy/*`.
- Delivery scheduling is modeled in-app and auto-updates status when due time is reached.
