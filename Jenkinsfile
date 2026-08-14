@Library('bwmp') _

// HoloPanels is the Paper-only build: it uses the server's Adventure rather
// than Keystone's shaded copy. If that exclusion regresses, holopanels-api
// starts publishing relocated Component types and every third-party provider
// breaks at runtime against a class name that looks correct — so the absence of
// kyori is asserted, not assumed.
mavenPlugin(
    artifacts: 'holopanels-plugin/target/HoloPanels-*.jar,holopanels-api/target/holopanels-api-*.jar',
    verify: [
        jar:       'holopanels-plugin/target/HoloPanels-*.jar',
        relocated: ['dev/bwmp/holopanels/libs/keystone/'],
        absent:    ['net/kyori/', 'dev/bwmp/keystone/']
    ]
)
