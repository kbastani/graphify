Identify Authors of a Text
==========================

This example ingests a set of texts from presidential speeches with labels from the author of that speech in training phase. After building the training models, unlabeled presidential speeches are classified in the test phase.

Presidential Speeches
---------------------

Below you will find two document indexes, one for training and one for testing. The training phase articles contain a set of speeches chosen early on in a president's campaign to the White House or speeches from their first term in office.

Training Phase
---------------------

* Ronald Reagan (/resources/reagan/training)
    * A TIME FOR CHOOSING (The Speech – October 27, 1964)
        * Labels: ronald-reagan, republican
    * RONALD REAGAN ANNOUNCEMENT FOR PRESIDENTIAL CANDIDACY (NOVEMBER, 20, 1975)
        * Labels: ronald-reagan, republican
    * To Restore America, Ronald Reagan’s Campaign Address (March 31, 1976)
        * Labels: ronald-reagan, republican
    * Republican National Convention (August 19, 1976)
        * Labels: ronald-reagan, republican

* George H. W. Bush (/resources/bush41/training)
    * Acceptance Speech at the Republican National Convention (August 18, 1988)
        * Labels: bush41, republican
    * Inaugural Address (January 20, 1989)
        * Labels: bush41, republican
    * Address Before a Joint Session of Congress (February 9, 1989)
        * Labels: bush41, republican
    * State of the Union Address (January 31, 1990)
        * Labels: bush41, republican

* Bill Clinton (/resources/clinton/training)
    * First Inaugural (January 20, 1993)
        * Labels: bill-clinton, democrat
    * Remarks at the Signing of the Family Medical Leave Act (February 5, 1993)
        * Labels: bill-clinton, democrat
    * Address Before a Joint Session of Congress (February 17, 1993)
        * Labels: bill-clinton, democrat
    * State of the Union Address (January 25, 1994)
        * Labels: bill-clinton, democrat

* George W. Bush (/resources/bush43/training)
    * First Inaugural Address (January 20, 2001)
        * Labels: bush43, republican
    * Remarks on Signing the Economic Growth and Tax Relief Reconciliation Act (June 7, 2001)
        * Labels: bush43, republican
    * State of the Union Address (January 29, 2002)
        * Labels: bush43, republican
    * State of the Union Address (January 28, 2003)
        * Labels: bush43, republican

* Barack Obama (/resources/obama/training)
    * Acceptance Speech at the Democratic National Convention (August 28, 2008)
        * Labels: barack-obama, democrat
    * Remarks on Election Night (November 4, 2008)
        * Labels: barack-obama, democrat
    * Inaugural Address (January 20, 2009)
        * Labels: barack-obama, democrat
    * Address Before a Joint Session of Congress (February 24, 2009)
        * Labels: barack-obama, democrat
    * Address to Congress on Health Care (September 9, 2009)
    * State of the Union Address (January 27, 2010)

Testing Phase
---------------------

* Ronald Reagan (/resources/reagan/test)
    * First Inaugural Address (January 20, 1981)
    * State of the Union Address (January 26, 1982)

* George H. W. Bush (/resources/bush41/test)
    * Address on Iraq’s Invasion of Kuwait (August 8, 1990)
    * Address Before a Joint Session of Congress (September 11, 1990)

* Bill Clinton (/resources/clinton/test)
    * Remarks at the Signing of the Israeli-Palestinian Agreement (September 13, 1993)
    * Address on Health Care Reform (September 22, 1993)

* George W. Bush (/resources/bush43/test)
    * State of the Union Address (January 20, 2004)
    * Second Inaugural Address (January 20, 2005)

* Barack Obama (/resources/obama/test)
    * Address at Cairo University (June 4, 2009)
    * State of the Union Address (January 27, 2010)