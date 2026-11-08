package com.telehex.telex.predict

import java.util.Locale

/** Từ điển tiếng Anh nhỏ, biên soạn trong dự án; chỉ hoàn thành tiền tố, không tự sửa hay học. */
class EnglishPredictor(words: List<String> = COMMON_WORDS) {
    private val dictionary = words.map { it.lowercase(Locale.ROOT) }.distinct()

    fun complete(prefix: String, max: Int = 24, capsLock: Boolean = false): List<String> {
        if (prefix.isEmpty() || prefix.length > 32 || max <= 0 || prefix.first() !in 'a'..'z' && prefix.first() !in 'A'..'Z') return emptyList()
        if (prefix.any { !it.isAsciiLetter() && it != '\'' }) return emptyList()
        val key = prefix.lowercase(Locale.ROOT)
        val upper = capsLock || (prefix.length > 1 && prefix.filter { it.isAsciiLetter() }.all { it.isUpperCase() })
        return dictionary.asSequence().filter { it.length > key.length && it.startsWith(key) }
            .take(max).map { if (upper) it.uppercase(Locale.ROOT) else prefix + it.substring(key.length) }.toList()
    }

    data class Token(val head: String, val tail: String)

    companion object {
        /** Bỏ qua email, URL, identifier và phần từ chứa chữ ngoài ASCII. */
        fun tokenAtCursor(before: String, after: String): Token? {
            val head = before.takeLastWhile { it.isAsciiLetter() || it == '\'' }
            val tail = after.takeWhile { it.isAsciiLetter() || it == '\'' }
            if (head.isEmpty() || head.length + tail.length > 32) return null
            val left = before.getOrNull(before.length - head.length - 1)
            val right = after.getOrNull(tail.length)
            if (left != null && (left.isLetterOrDigit() || left in "_@./\\:$-")) return null
            if (right != null && (right.isLetterOrDigit() || right in "_@/\\:$-")) return null
            if (right == '.' && after.getOrNull(tail.length + 1)?.isLetterOrDigit() == true) return null
            return Token(head, tail)
        }

        private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

        // Thứ tự ưu tiên thủ công cho từ giao tiếp và công việc thường dùng; không dùng corpus bên ngoài.
        private val COMMON_WORDS = """
            the to and of a in is it you that for on with this have be are not your as at we can from
            will all was if my one about do what so me out up there when how they an just like our
            please thanks thank hello help helpful helping helped hey hi happy happiness happen happens
            good great get give go going got gone getting given giving game games group guide guess guest
            home hope house hour hours here hear heard heart health healthy history hold hotel holiday
            i i'm i've i'll i'd am aren't isn't wasn't weren't don't doesn't didn't can't couldn't won't
            wouldn't shouldn't it's that's there's here's let's you're you've you'll we're we've we'll
            they're they've they'll who's what's where's how's cannot could would should may might must
            today tomorrow tonight yesterday morning afternoon evening night week weekend month year time
            now then soon later early late before after again always already also almost another anyone
            anything anywhere around away back because become been being below between both bring brought
            but by call called calling came come comes coming copy cut paste undo redo save saved saving
            send sent sending see seen seeing say said says saying speak spoke spoken speaking tell told
            take taken taking takes talk talking think thought things thing through together too try trying
            use used using uses user users usually update updated updates upload download online offline
            open opened opening close closed closing clear clean check checked checking choose chose chosen
            click change changed changes changing create created creating current custom contact contacts
            message messages mail email letter letters note notes text type typing word words write writing
            wrote written read reading reader really ready right wrong left center top bottom front behind
            work working worked worker workers world want wanted wants wish wait waiting watch watching
            water warm walk walking way ways well welcome went were where which while why who whom whose
            yes yet no never new news next need needed needs nice name names named near nearly necessary
            number numbers nothing nobody normal north noone none notice notification notifications
            every everyone everything each either else enough even ever example excellent except extra
            first second third last long longer look looking love lovely live living life little light
            listen listening list link location local low lower large larger learn learning learned leave
            let less least line lines load loading loaded language languages laptop launch layout limit
            more most much many make makes making made meet meeting meetings maybe mean means meant minute
            minutes money move moving moved music model models mobile member members manager memory middle
            only other others over own once often off offer offers office official option options order
            old older okay ok outside output operation original orange owner organize organized
            people person personal place places plan plans planning play playing point possible problem
            problems project projects put picture pictures photo photos phone private public process program
            programming progress profile profiles password permission permissions post position practice
            pay paid price product products page pages panel part party past perfect perhaps period pick
            pretty previous probably provide push pull promise proper protect print preview prepare present
            find found finding file files folder folders full free friend friends family fast faster feature
            features feel feeling few fine finish finished fix fixed fixes follow following food forget
            forgot forgotten form format forward favorite feedback face fact far father final finally
            function functions future further fresh field fields focus force frequent frequency friday
            question questions quick quickly quiet quite query quality quarter queue quote quoted
            answer ask asked asking action actions activity active account accounts add added address
            allow allowed application applications app apps able above access across actual actually adjust
            advance advice agree air alarm album alert almost along amount analysis android apple april area
            article artist available avoid automatic automatically average amazing attach attachment audio
            best better big small size start started starting stop stopped strong sure support system
            set setting settings show shown shows some someone something sometimes somewhere still same
            search select selection selected selection share shared sharing short simple simply since
            screen screenshot scroll service security sensitive secret section sentence sentence sound
            source space special speed standard status step steps store stored storage style subject
            success successful suggest suggestion suggestions summary summer sunday saturday stable symbol
            sync science school season story stories safe safer sorry smile sort soon sleep sleeping
            switch structure state sample setup sign signed silent single similar slow slowest special
            test tests testing tested team teams tool tools touch touches track trace true false trust
            turn turns token table tablet target task tasks technical technology terminal theme themes
            term terms temporary title total training traffic travel trial trouble Tuesday Thursday
            thank thankful three two ten twenty thousand thirty twice translation translate temperature
            understand understood understanding until under unit units unlimited useful username unique
            video videos view views visible voice value values version verify verified visit visitor
            validation valid volume vocabulary vote very via variety variable variables various vehicle
            window windows without within whether whole wide width wish wifi wireless win winner winter
            welcome website web weather week weekly warning welcome wait waiting warm western white
            zoom zero zone zip yellow young your yours yourself xray yesterday year yearly yield
            build building built book books break broken browser business button buttons buy buying bought
            backup balance base basic battery beautiful begin beginning beginning believe benefit birthday
            bit black blue body bonus border box branch brief bright busy byte bytes broad bottom both
            data date dates day days debug default delete deleted description design device devices did
            difference different difficult direction direct directly display document documents done down
            drive driver during dark dashboard database decide decision detail details develop developer
            development dictionary distance double draft draw drawing drop drag duration desktop dialog
            engineer error errors event events edit editor editing enable enabled disabled disable end
            enter entry environment escape empty expected export external extend extension effect effort
            eight eleven empty element english engine example expand expanded exact exercise experience
            import important include included input inputs install installed instance internal internet
            information info issue issues item items image images improve improved indication interest
            identity ignore implement implementation initial increase independent index individual instead
            january february march may june july august september october november december monday wednesday
            red green gray black white purple pink brown gold silver gray grey bright dark color colors
            cat dog bird fish animal animals child children mother father brother sister son daughter
            coffee tea milk bread rice dinner lunch breakfast restaurant kitchen room road city country
            car bus train bike airplane airport station ticket map trip beach mountain river rain sun
            birthday congratulations nice wonderful cool awesome awesome enjoy easy easier hardest hard
            run running ran result results return returns received receive receiving request response
            record recording records recent recently recommend recover recovery release releases repeat
            reply report reports retry restore reset remove removed remaining remember remembered reference
            refresh replace replacement repository resolve resolution resource resources review reviews
            reason reasons reduce relevant required requirement requirements restart resize return reuse
            connection connected connect configuration confirm confirmation correct correction corrections
            character characters cursor clipboard commit commits comparison compare content contents control
            condition context continue continued conversation complete completed completion completions
            cancel cancelled capacity capture category cell cells certificate code coding column columns
        """.trimIndent().split(Regex("\\s+")).distinct()
    }
}
