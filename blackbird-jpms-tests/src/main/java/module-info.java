// A foreign named module from Blackbird's point of view: it requires the
// blackbird module and holds non-public beans plus a lookup of its own, the
// exact arrangement a modular application has.
module blackbird.jpms.test
{
    requires tools.jackson.databind;
    requires tools.jackson.module.blackbird;

    exports blackbird.jpms.test.beans;
    // What a modular application must do for stock databind to construct
    // non-public beans reflectively - and the ONLY requirement blackbird
    // adds: none. Deliberately NOT opened to blackbird: the accelerated path
    // rides databind's fixAccess through unreflected constant handles.
    opens blackbird.jpms.test.beans to tools.jackson.databind;
}
