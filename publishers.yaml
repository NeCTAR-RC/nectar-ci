#  common publisher definitions
#
- slack_notifier: &slack_notifier
    name: "slack_notifier"
      - slack:
        room: '#dev'
        notify-failure: True
        notify-repeated-failure: True
        include-test-summary: True
        include-failed-tests: True
        include-custom-message: True
        custom-message: 'A custom message.'
        auth-token-credential-id: slack-key
        bot-user: False

